-- =====================================================================
-- MyBusiness Silva — V40 (tenant): segunda barrera de BD para el invariante
-- "no se saca lo que no hay" sobre inventory_stock.
--
-- Número de versión: V40. La más alta tenant es V39__sales_return_shift.
--
-- La aplicación ya descuenta el stock normal de forma estricta y atómica
-- (applyOutboundStrict con WHERE quantity >= qty e InsufficientStockException).
-- Esta migración añade la SEGUNDA BARRERA en la base: un CHECK (quantity >= 0)
-- sobre inventory_stock, de modo que ninguna ruta pueda dejar existencias
-- negativas aunque burlara la capa de aplicación. inventory_stock.quantity es
-- NUMERIC(14,3) NOT NULL DEFAULT 0 y nació SIN este CHECK (V3__inventory.sql).
--
-- ATENCIÓN PRODUCCIÓN: el paso (1) SANEA los negativos existentes llevándolos a
-- cero. Ese saneamiento es IRREVERSIBLE (no se puede recuperar el valor negativo
-- previo) y requiere APROBACIÓN DEL DUEÑO antes de ejecutar, porque un negativo
-- suele delatar un hueco de datos que conviene revisar antes de aplanarlo. No se
-- ejecuta automáticamente en este cambio.
--
-- Idempotente y segura en Postgres (UPDATE condicional + DROP IF EXISTS + ADD).
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

-- (1) Saneamiento IRREVERSIBLE de negativos preexistentes: los lleva a cero.
UPDATE ${tenant_schema}.inventory_stock
    SET quantity = 0, updated_at = now()
    WHERE quantity < 0;

-- (2) Reemplaza el CHECK para que sea idempotente (re-ejecutable).
ALTER TABLE ${tenant_schema}.inventory_stock
    DROP CONSTRAINT IF EXISTS chk_inventory_stock_nonneg;

-- (3) Segunda barrera: la existencia nunca puede ser negativa.
ALTER TABLE ${tenant_schema}.inventory_stock
    ADD CONSTRAINT chk_inventory_stock_nonneg CHECK (quantity >= 0);
