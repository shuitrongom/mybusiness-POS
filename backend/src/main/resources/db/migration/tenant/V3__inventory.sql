-- =====================================================================
-- Inventario por sucursal (por tenant).
--   - inventory_stock: existencia actual de un producto en una sucursal.
--   - inventory_movement: kardex (histórico de entradas/salidas/ajustes/traspasos).
--   - product_lot: lotes y caducidades por producto/sucursal.
-- Todas con RLS por tenant.
-- =====================================================================

-- Existencia actual por producto y sucursal.
CREATE TABLE ${tenant_schema}.inventory_stock (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    product_id   BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    branch_id    BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    quantity     NUMERIC(14,3) NOT NULL DEFAULT 0,
    min_quantity NUMERIC(14,3) NOT NULL DEFAULT 0,   -- stock mínimo (para alertas)
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_stock_product_branch UNIQUE (product_id, branch_id)
);

-- Kardex: histórico de movimientos de inventario.
CREATE TABLE ${tenant_schema}.inventory_movement (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    product_id     BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    branch_id      BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    movement_type  VARCHAR(20)   NOT NULL,   -- PURCHASE, SALE, ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT, RETURN
    quantity       NUMERIC(14,3) NOT NULL,   -- positivo entra, negativo sale
    balance_after  NUMERIC(14,3) NOT NULL,   -- existencia resultante tras el movimiento
    reason         VARCHAR(300),
    reference      VARCHAR(120),             -- referencia (folio de venta/compra/traspaso)
    actor          VARCHAR(255),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_movement_type CHECK (movement_type IN
        ('PURCHASE','SALE','ADJUSTMENT','TRANSFER_IN','TRANSFER_OUT','RETURN'))
);

-- Lotes y caducidades por producto/sucursal.
CREATE TABLE ${tenant_schema}.product_lot (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    product_id      BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    branch_id       BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    lot_code        VARCHAR(80),
    expiration_date DATE,
    quantity        NUMERIC(14,3) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_product   ON ${tenant_schema}.inventory_stock (product_id);
CREATE INDEX idx_stock_branch    ON ${tenant_schema}.inventory_stock (branch_id);
CREATE INDEX idx_movement_prod   ON ${tenant_schema}.inventory_movement (product_id, created_at);
CREATE INDEX idx_lot_expiration  ON ${tenant_schema}.product_lot (expiration_date);

-- Row-Level Security.
ALTER TABLE ${tenant_schema}.inventory_stock    ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.inventory_movement ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.product_lot        ENABLE ROW LEVEL SECURITY;

ALTER TABLE ${tenant_schema}.inventory_stock    FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.inventory_movement FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.product_lot        FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_stock ON ${tenant_schema}.inventory_stock
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY tenant_isolation_movement ON ${tenant_schema}.inventory_movement
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY tenant_isolation_lot ON ${tenant_schema}.product_lot
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
