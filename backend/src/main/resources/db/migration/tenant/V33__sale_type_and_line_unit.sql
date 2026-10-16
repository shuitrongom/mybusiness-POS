-- =====================================================================
-- MyBusiness Silva — V33 (tenant): clasificación de VENTA ESPECIAL.
--
-- sale.sale_type: 'NORMAL' (solo catálogo) o 'SPECIAL' (al menos un artículo
--   libre, product_id NULL). Se calcula en backend; NO se confía en el front.
--   Ventas existentes y solo-catálogo quedan 'NORMAL' (backfill por DEFAULT).
-- sale_line.unit: unidad del renglón (pz, kg, ...). El front ya la envía;
--   nullable para no romper renglones históricos ni ventas solo-catálogo.
-- cash_cut.*_normal / *_special: split del corte para el reporte del admin.
--   Cortes históricos quedan 0/0; NO se re-split (no había sale_type antes).
--
-- Se fija app.current_tenant por consistencia con V30/V31; el DDL (ALTER/
-- CREATE INDEX) NO está sujeto a RLS (RLS gobierna DML). Idempotente.
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

-- (1) Clasificación de la venta.
ALTER TABLE ${tenant_schema}.sale
    ADD COLUMN IF NOT EXISTS sale_type VARCHAR(16) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE ${tenant_schema}.sale
    DROP CONSTRAINT IF EXISTS chk_sale_type;
ALTER TABLE ${tenant_schema}.sale
    ADD CONSTRAINT chk_sale_type CHECK (sale_type IN ('NORMAL','SPECIAL'));

CREATE INDEX IF NOT EXISTS idx_sale_type ON ${tenant_schema}.sale (sale_type);

-- (2) Unidad del renglón (producto rápido / venta aislada). Nullable.
ALTER TABLE ${tenant_schema}.sale_line
    ADD COLUMN IF NOT EXISTS unit VARCHAR(16);

-- (3) Split NORMAL/ESPECIAL persistido en el documento de corte (X/Z).
ALTER TABLE ${tenant_schema}.cash_cut
    ADD COLUMN IF NOT EXISTS normal_sales_count  INTEGER       NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.cash_cut
    ADD COLUMN IF NOT EXISTS normal_sales_total  NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.cash_cut
    ADD COLUMN IF NOT EXISTS special_sales_count INTEGER       NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.cash_cut
    ADD COLUMN IF NOT EXISTS special_sales_total NUMERIC(14,2) NOT NULL DEFAULT 0;
