-- =====================================================================
-- Ventas y caja (por tenant).
--   - cash_register: cajas registradoras por sucursal.
--   - shift: turnos de caja (apertura/cierre, arqueo).
--   - sale / sale_line / sale_payment: ventas, sus renglones y pagos (mixtos).
--   - cash_movement: entradas/salidas de efectivo (retiros, gastos).
-- La venta lleva 'idempotency_key' única para soportar sincronización offline sin duplicados.
-- Todas con RLS por tenant.
-- =====================================================================

-- Cajas registradoras.
CREATE TABLE ${tenant_schema}.cash_register (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    branch_id  BIGINT       NOT NULL REFERENCES ${tenant_schema}.branch(id),
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Turnos de caja.
CREATE TABLE ${tenant_schema}.shift (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    cash_register_id BIGINT      NOT NULL REFERENCES ${tenant_schema}.cash_register(id),
    opened_by      VARCHAR(255)  NOT NULL,
    opening_float  NUMERIC(14,2) NOT NULL DEFAULT 0,   -- fondo de caja inicial
    opened_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_by      VARCHAR(255),
    counted_cash   NUMERIC(14,2),                      -- efectivo contado (arqueo)
    closed_at      TIMESTAMPTZ,
    status         VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    CONSTRAINT chk_shift_status CHECK (status IN ('OPEN','CLOSED'))
);

-- Ventas.
CREATE TABLE ${tenant_schema}.sale (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    folio           VARCHAR(40),
    branch_id       BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    cash_register_id BIGINT       REFERENCES ${tenant_schema}.cash_register(id),
    shift_id        BIGINT        REFERENCES ${tenant_schema}.shift(id),
    cashier         VARCHAR(255),
    customer_id     BIGINT,
    subtotal        NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax             NUMERIC(14,2) NOT NULL DEFAULT 0,
    total           NUMERIC(14,2) NOT NULL DEFAULT 0,
    status          VARCHAR(12)   NOT NULL DEFAULT 'COMPLETED',  -- COMPLETED, VOIDED, QUOTE
    idempotency_key VARCHAR(80)   UNIQUE,                        -- para sincronización offline
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sale_status CHECK (status IN ('COMPLETED','VOIDED','QUOTE'))
);

-- Renglones de venta.
CREATE TABLE ${tenant_schema}.sale_line (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    sale_id     BIGINT        NOT NULL REFERENCES ${tenant_schema}.sale(id) ON DELETE CASCADE,
    product_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    description VARCHAR(300)  NOT NULL,
    quantity    NUMERIC(14,3) NOT NULL,
    unit_price  NUMERIC(14,2) NOT NULL,
    discount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total  NUMERIC(14,2) NOT NULL
);

-- Pagos de la venta (permite pagos mixtos: varios renglones por venta).
CREATE TABLE ${tenant_schema}.sale_payment (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    sale_id     BIGINT        NOT NULL REFERENCES ${tenant_schema}.sale(id) ON DELETE CASCADE,
    method      VARCHAR(20)   NOT NULL,   -- CASH, CARD, TRANSFER, VOUCHER
    amount      NUMERIC(14,2) NOT NULL,
    CONSTRAINT chk_payment_method CHECK (method IN ('CASH','CARD','TRANSFER','VOUCHER'))
);

-- Movimientos de efectivo (entradas/salidas: retiros, gastos, ingresos).
CREATE TABLE ${tenant_schema}.cash_movement (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    shift_id    BIGINT        NOT NULL REFERENCES ${tenant_schema}.shift(id),
    direction   VARCHAR(10)   NOT NULL,   -- IN, OUT
    amount      NUMERIC(14,2) NOT NULL,
    reason      VARCHAR(300),
    actor       VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_cash_direction CHECK (direction IN ('IN','OUT'))
);

CREATE INDEX idx_sale_created   ON ${tenant_schema}.sale (created_at);
CREATE INDEX idx_sale_shift     ON ${tenant_schema}.sale (shift_id);
CREATE INDEX idx_saleline_sale  ON ${tenant_schema}.sale_line (sale_id);
CREATE INDEX idx_salepay_sale   ON ${tenant_schema}.sale_payment (sale_id);

-- Row-Level Security.
ALTER TABLE ${tenant_schema}.cash_register ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.shift         ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.sale          ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.sale_line     ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.sale_payment  ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.cash_movement ENABLE ROW LEVEL SECURITY;

ALTER TABLE ${tenant_schema}.cash_register FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.shift         FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.sale          FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.sale_line     FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.sale_payment  FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.cash_movement FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_cash_register ON ${tenant_schema}.cash_register
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_shift ON ${tenant_schema}.shift
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_sale ON ${tenant_schema}.sale
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_sale_line ON ${tenant_schema}.sale_line
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_sale_payment ON ${tenant_schema}.sale_payment
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_cash_movement ON ${tenant_schema}.cash_movement
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
