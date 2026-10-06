-- =====================================================================
-- MyBusiness Silva — V14 (tenant): Punto de venta ENTERPRISE.
--
-- Bloque 1 de la Suite de Ventas. Aporta lo que faltaba para superar a los
-- POS líderes del mercado en el flujo de cobro:
--   1) Nombres configurables de las listas de precio (multiprecio) por negocio.
--   2) Venta a crédito integrada: la venta guarda cuánto se pagó y si quedó a
--      crédito; la parte no pagada genera una cuenta por cobrar del cliente.
--   3) Descuento global de la venta (además del descuento por renglón).
--   4) Metadatos comerciales de la venta: vendedor, lista de precio usada, nota.
--
-- Todo idempotente (IF NOT EXISTS) y con defaults que no alteran el
-- comportamiento de las ventas ya registradas. Flyway sustituye ${tenant_schema}.
-- =====================================================================

-- ---- 1) Catálogo de listas de precio del negocio (nombres del multiprecio) ----
-- price (nivel 1) ya vive en product.price; price2..price5 en product. Aquí solo
-- guardamos CÓMO se llama cada nivel para este negocio (Público, Mayoreo, etc.).
CREATE TABLE IF NOT EXISTS ${tenant_schema}.price_list (
    id         SMALLINT      PRIMARY KEY,                      -- 1..5 (nivel de precio)
    tenant_id  VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    name       VARCHAR(60)   NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE
);

-- Semillas por defecto (los 5 niveles con nombres comerciales estándar en México).
INSERT INTO ${tenant_schema}.price_list (id, name, active) VALUES
    (1, 'Público',          TRUE),
    (2, 'Mayoreo',          TRUE),
    (3, 'Medio mayoreo',    TRUE),
    (4, 'Especial',         FALSE),
    (5, 'Distribuidor',     FALSE)
ON CONFLICT (id) DO NOTHING;

-- ---- 2) y 3) Campos nuevos en la venta ----
-- Cuánto se recibió realmente (para venta a crédito, amount_paid < total).
ALTER TABLE ${tenant_schema}.sale ADD COLUMN IF NOT EXISTS amount_paid    NUMERIC(14,2) NOT NULL DEFAULT 0;
-- Bandera de venta a crédito (queda saldo pendiente que genera cuenta por cobrar).
ALTER TABLE ${tenant_schema}.sale ADD COLUMN IF NOT EXISTS on_credit      BOOLEAN       NOT NULL DEFAULT FALSE;
-- Descuento global de la venta (aparte del descuento por renglón).
ALTER TABLE ${tenant_schema}.sale ADD COLUMN IF NOT EXISTS global_discount NUMERIC(14,2) NOT NULL DEFAULT 0;
-- Vendedor (puede ser distinto del cajero que cobra).
ALTER TABLE ${tenant_schema}.sale ADD COLUMN IF NOT EXISTS salesperson    VARCHAR(255);
-- Lista de precio usada en la venta (1..5).
ALTER TABLE ${tenant_schema}.sale ADD COLUMN IF NOT EXISTS price_list_id  SMALLINT      NOT NULL DEFAULT 1;
-- Nota / observaciones de la venta.
ALTER TABLE ${tenant_schema}.sale ADD COLUMN IF NOT EXISTS note           VARCHAR(500);

-- Para ventas históricas ya pagadas de contado, amount_paid = total.
UPDATE ${tenant_schema}.sale
   SET amount_paid = total
 WHERE amount_paid = 0 AND status = 'COMPLETED' AND on_credit = FALSE;

-- ---- 4) Cuenta por cobrar: enlazar la sucursal y el saldo abierto ----
-- account_receivable ya existe (V7). Agregamos la sucursal de origen para cobranza por sucursal.
ALTER TABLE ${tenant_schema}.account_receivable ADD COLUMN IF NOT EXISTS branch_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_sale_customer ON ${tenant_schema}.sale (customer_id);
CREATE INDEX IF NOT EXISTS idx_sale_salesperson ON ${tenant_schema}.sale (salesperson);

-- Row-Level Security en la nueva tabla.
ALTER TABLE ${tenant_schema}.price_list ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.price_list FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_price_list ON ${tenant_schema}.price_list
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
