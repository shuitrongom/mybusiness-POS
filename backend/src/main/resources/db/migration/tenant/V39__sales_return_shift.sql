-- =====================================================================
-- MyBusiness Silva — V39 (tenant): liga la DEVOLUCIÓN al turno de caja.
--
-- Número de versión: V39. La más alta tenant es V38__inventory_movement_waste_out.
--
-- Añade sales_return.shift_id: el turno (shift) en que se procesó la devolución.
-- Lo usa el flujo de devoluciones para, cuando el reembolso es en EFECTIVO,
-- registrar una salida de caja (cash_movement OUT) ligada a ese turno, de modo
-- que el efectivo esperado del corte (openingFloat + ventas efectivo + entradas
-- - salidas) descuente el reembolso sin tocar las fórmulas del corte.
--
-- Nullable y sin default: devoluciones sin turno activo (o históricas) quedan en
-- NULL. Idempotente (ADD COLUMN IF NOT EXISTS). No modifica V1..V38.
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

ALTER TABLE ${tenant_schema}.sales_return
    ADD COLUMN IF NOT EXISTS shift_id BIGINT REFERENCES ${tenant_schema}.shift(id);
