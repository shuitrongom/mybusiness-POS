-- =====================================================================
-- MyBusiness Silva — V41 (tenant): marca de VENTA DE PRUEBA (modo prueba).
--
-- Número de versión: V41. La más alta tenant es V40
--   (V40__inventory_stock_nonneg.sql). No existe V41. => esta migración toma V41.
--
-- Añade sale.is_test y cash_cut.is_test: TRUE si la venta / el corte se registró
-- mientras el negocio estaba en MODO PRUEBA (admin.business.test_mode = true).
-- Permite separar en reportes las ventas/cortes de PRÁCTICA del histórico REAL
-- sin borrar nada (Opción B). NO se agrega a sale_line ni inventory_movement:
-- la marca vive solo en el documento de venta y en el corte; el inventario NO se
-- separa por modo prueba.
--
-- El modo prueba se decide server-side al registrar: SaleService setea
-- sale.is_test según admin.business.test_mode del tenant; ShiftService hace lo
-- mismo con cash_cut.is_test al persistir el corte.
--
-- Default FALSE: deja INTACTAS las ventas/cortes existentes (siguen como real).
-- Las ventas ya registradas de días pasados se marcan a MANO con el script
-- scripts/mark-existing-sales-as-test.sql (manual, NO Flyway).
--
-- Índice parcial por is_test para que los filtros de reportes (scope) sean
-- eficientes. Se fija app.current_tenant por consistencia con V33/V37; el DDL
-- (ALTER/CREATE INDEX) NO está sujeto a RLS. Idempotente. Flyway sustituye
-- ${tenant_schema}.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

ALTER TABLE ${tenant_schema}.sale
    ADD COLUMN IF NOT EXISTS is_test BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ${tenant_schema}.cash_cut
    ADD COLUMN IF NOT EXISTS is_test BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_sale_is_test
    ON ${tenant_schema}.sale (is_test);

CREATE INDEX IF NOT EXISTS idx_cash_cut_is_test
    ON ${tenant_schema}.cash_cut (is_test);
