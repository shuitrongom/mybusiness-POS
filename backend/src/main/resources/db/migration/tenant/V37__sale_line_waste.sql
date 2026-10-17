-- =====================================================================
-- MyBusiness Silva — V37 (tenant): marca de VENTA DE MERMA por renglón.
--
-- Número de versión: V37. La más alta tenant es V36__bakery_waste_and_price.
--
-- Añade sale_line.waste: TRUE si el renglón se vendió como MERMA de panadería
-- (precio de merma fijo del artículo, descontado de bakery_waste_stock y no del
-- stock normal). Permite:
--   - excluir el renglón del descuento de stock normal al registrar la venta;
--   - al cancelar la venta, devolver la cantidad a la EXISTENCIA DE MERMA en vez
--     de reingresarla al stock normal;
--   - distinguir en reportes las ventas de merma de las normales.
--
-- Default FALSE: inocuo para ventas normales y otros giros. Idempotente.
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

ALTER TABLE ${tenant_schema}.sale_line
    ADD COLUMN IF NOT EXISTS waste BOOLEAN NOT NULL DEFAULT FALSE;
