-- =====================================================================
-- MyBusiness Silva — V16 (tenant): DEVOLUCIONES reales.
--
-- Bloque 3 de la Suite de Ventas. Hasta ahora una "devolución" solo reingresaba
-- inventario sin dejar rastro. Aquí se persiste la devolución como documento,
-- ligada a la venta original, con sus renglones, el motivo, el reembolso y quién
-- la procesó. Esto permite:
--   • Validar que no se devuelva más de lo vendido (menos lo ya devuelto).
--   • Consultar el histórico de devoluciones por venta.
--   • Registrar el reembolso (efectivo, nota de crédito o saldo a favor).
--
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

-- Encabezado de la devolución.
CREATE TABLE ${tenant_schema}.sales_return (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    folio         VARCHAR(40),
    sale_id       BIGINT        REFERENCES ${tenant_schema}.sale(id),   -- venta origen (puede ser null: devolución libre)
    branch_id     BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    customer_id   BIGINT,
    reason        VARCHAR(300),
    total         NUMERIC(14,2) NOT NULL DEFAULT 0,     -- importe devuelto
    refund_method VARCHAR(20)   NOT NULL DEFAULT 'CASH', -- CASH, CREDIT_NOTE, STORE_CREDIT
    processed_by  VARCHAR(255),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_return_refund CHECK (refund_method IN ('CASH','CREDIT_NOTE','STORE_CREDIT'))
);

-- Renglones de la devolución.
CREATE TABLE ${tenant_schema}.sales_return_line (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    return_id   BIGINT        NOT NULL REFERENCES ${tenant_schema}.sales_return(id) ON DELETE CASCADE,
    product_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    description VARCHAR(300)  NOT NULL,
    quantity    NUMERIC(14,3) NOT NULL,
    unit_price  NUMERIC(14,2) NOT NULL,
    line_total  NUMERIC(14,2) NOT NULL
);

CREATE INDEX idx_return_sale   ON ${tenant_schema}.sales_return (sale_id);
CREATE INDEX idx_return_created ON ${tenant_schema}.sales_return (created_at);
CREATE INDEX idx_returnline_ret ON ${tenant_schema}.sales_return_line (return_id);

-- Row-Level Security.
ALTER TABLE ${tenant_schema}.sales_return      ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.sales_return_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.sales_return      FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.sales_return_line FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_sales_return ON ${tenant_schema}.sales_return
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_sales_return_line ON ${tenant_schema}.sales_return_line
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
