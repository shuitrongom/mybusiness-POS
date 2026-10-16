-- =====================================================================
-- MyBusiness Silva — V31 (tenant): PRECIOS ESPECIALES POR CLIENTE.
--
-- Permite asignar a un cliente un precio especial por producto, que el POS
-- usa como fuente de verdad cuando ese cliente está seleccionado. Es la base
-- del módulo de precios por cliente (el frontend lo consume vía el mapa
-- producto -> precio especial).
--
-- Tabla hija de customer y product: si se borra el cliente o el producto, sus
-- precios especiales se eliminan en cascada. Un cliente no puede tener dos
-- precios para el mismo producto (UNIQUE), y el precio nunca es negativo
-- (CHECK). Idempotente (IF NOT EXISTS) y con RLS como el resto de tablas de
-- tenant. Flyway sustituye ${tenant_schema}.
-- =====================================================================

CREATE TABLE IF NOT EXISTS ${tenant_schema}.customer_price (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    customer_id BIGINT        NOT NULL REFERENCES ${tenant_schema}.customer(id) ON DELETE CASCADE,
    product_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id) ON DELETE CASCADE,
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_price UNIQUE (customer_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_customer_price_customer
    ON ${tenant_schema}.customer_price (customer_id);

-- Row-Level Security: aislamiento estricto por tenant.
ALTER TABLE ${tenant_schema}.customer_price ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.customer_price FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_customer_price ON ${tenant_schema}.customer_price
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
