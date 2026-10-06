-- =====================================================================
-- MyBusiness Silva — V18 (tenant): ANTICIPOS de clientes.
--
-- Bloque 5 de la Suite de Ventas. Un anticipo es dinero que el cliente entrega
-- por adelantado (a cuenta de pedidos o compras futuras). Se lleva como un saldo
-- por cliente con su historial de movimientos:
--   • DEPOSIT — el cliente abona/deposita al anticipo (aumenta el saldo).
--   • APPLY   — se aplica parte del anticipo a una venta (disminuye el saldo).
--   • REFUND  — se devuelve saldo al cliente (disminuye el saldo).
--
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

-- Saldo de anticipo por cliente (uno por cliente).
CREATE TABLE ${tenant_schema}.customer_advance (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    customer_id BIGINT        NOT NULL UNIQUE REFERENCES ${tenant_schema}.customer(id),
    balance     NUMERIC(14,2) NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Movimientos del anticipo.
CREATE TABLE ${tenant_schema}.customer_advance_movement (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    customer_id   BIGINT        NOT NULL REFERENCES ${tenant_schema}.customer(id),
    direction     VARCHAR(10)   NOT NULL,   -- DEPOSIT, APPLY, REFUND
    amount        NUMERIC(14,2) NOT NULL,
    balance_after NUMERIC(14,2) NOT NULL,
    method        VARCHAR(20),              -- método del depósito (CASH, CARD, TRANSFER)
    sale_id       BIGINT,                   -- venta a la que se aplicó (en APPLY)
    reference     VARCHAR(200),
    actor         VARCHAR(255),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_advance_direction CHECK (direction IN ('DEPOSIT','APPLY','REFUND'))
);

CREATE INDEX idx_advance_customer  ON ${tenant_schema}.customer_advance (customer_id);
CREATE INDEX idx_advmov_customer   ON ${tenant_schema}.customer_advance_movement (customer_id);
CREATE INDEX idx_advmov_created    ON ${tenant_schema}.customer_advance_movement (created_at);

-- Row-Level Security.
ALTER TABLE ${tenant_schema}.customer_advance          ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.customer_advance_movement ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.customer_advance          FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.customer_advance_movement FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_customer_advance ON ${tenant_schema}.customer_advance
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_customer_advance_movement ON ${tenant_schema}.customer_advance_movement
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
