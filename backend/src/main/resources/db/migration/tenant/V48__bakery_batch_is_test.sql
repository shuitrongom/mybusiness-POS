-- =====================================================================
-- MyBusiness Silva — V48 (tenant): marca de TANDA DE PRUEBA (modo prueba).
--
-- Número de versión: V48. La más alta tenant es V47
--   (V47__raw_material_and_bakery_batch.sql). No existe V48. => esta migración
--   toma V48. ⚠️ Si un workflow panadero en paralelo ya tomó V48, renombrar este
--   archivo a V49 con contenido idéntico.
--
-- Añade bakery_batch.is_test: TRUE si la tanda se registró mientras el negocio
-- estaba en MODO PRUEBA (admin.business.test_mode = true), gobernado por el MISMO
-- switch del dueño que ya marca sale.is_test / cash_cut.is_test (V41). Permite
-- separar en reportes/rendimiento las tandas de PRÁCTICA del histórico REAL sin
-- borrar nada (Opción B, coherente con ventas/cortes).
--
-- El modo prueba se decide server-side al registrar: BakeryBatchService setea
-- bakery_batch.is_test según admin.business.test_mode del tenant (igual que
-- SaleService.markTest). El cliente nunca lo manda.
--
-- Default FALSE: deja INTACTAS las tandas existentes (siguen como real).
--
-- Índice sobre is_test para que los filtros de rendimiento (recentYieldsForSameType,
-- yieldReport) sean eficientes. ⚠️ El DDL es un índice TOTAL (sin cláusula WHERE),
-- replicando el patrón REAL de V41 (idx_sale_is_test / idx_cash_cut_is_test son
-- TOTALES). NO convertir a índice parcial "para seguir a V41": el índice real de
-- V41 NO es parcial.
--
-- Se fija app.current_tenant por consistencia con V41/V47; el DDL (ALTER/CREATE
-- INDEX) NO está sujeto a RLS. Idempotente. Flyway sustituye ${tenant_schema}.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

ALTER TABLE ${tenant_schema}.bakery_batch
    ADD COLUMN IF NOT EXISTS is_test BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_bakery_batch_is_test
    ON ${tenant_schema}.bakery_batch (is_test);
