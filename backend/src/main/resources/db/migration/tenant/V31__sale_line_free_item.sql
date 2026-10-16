-- =====================================================================
-- MyBusiness Silva — V31 (tenant): artículo libre en el renglón de venta
-- (producto rápido / venta aislada).
--
-- Permite registrar renglones de venta sin producto del catálogo: la columna
-- sale_line.product_id pasa a ser NULLABLE. Para que al eliminar un producto
-- del catálogo no se rompan ventas históricas, la FK se recrea con
-- ON DELETE SET NULL (el renglón queda como artículo libre con su descripción).
--
-- Fija app.current_tenant para la RLS. Idempotente. Flyway sustituye ${tenant_schema}.
-- La FK inline original (V4) es auto-nombrada por Postgres: sale_line_product_id_fkey
-- (<tabla>_<columna>_fkey). El DROP usa IF EXISTS, por lo que un nombre distinto
-- no es fatal.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

ALTER TABLE ${tenant_schema}.sale_line ALTER COLUMN product_id DROP NOT NULL;

ALTER TABLE ${tenant_schema}.sale_line DROP CONSTRAINT IF EXISTS sale_line_product_id_fkey;

ALTER TABLE ${tenant_schema}.sale_line
    ADD CONSTRAINT sale_line_product_id_fkey
    FOREIGN KEY (product_id) REFERENCES ${tenant_schema}.product(id) ON DELETE SET NULL;
