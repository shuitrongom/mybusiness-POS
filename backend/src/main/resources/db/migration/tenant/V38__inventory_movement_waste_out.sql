-- =====================================================================
-- MyBusiness Silva — V38 (tenant): permitir WASTE_OUT en inventory_movement.
--
-- Número de versión: V38. La más alta tenant es V37__sale_line_waste.
--
-- El modelo de MERMA de panadería introdujo el tipo de movimiento WASTE_OUT
-- (salida del stock normal porque el pan pasó a merma vendible). El enum Java
-- MovementType ya lo incluye, pero la restricción CHECK chk_movement_type
-- definida en V3__inventory.sql sólo permitía:
--   PURCHASE, SALE, ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT, RETURN
-- Por eso "pasar pan a merma" (POST /bakery/waste/transfer) fallaba con
-- "viola la restricción check chk_movement_type".
--
-- Esta migración reemplaza la restricción para incluir WASTE_OUT, usando el
-- enum MovementType como única fuente de verdad.
--
-- Idempotente y segura en Postgres (DROP IF EXISTS + ADD).
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

ALTER TABLE ${tenant_schema}.inventory_movement
    DROP CONSTRAINT IF EXISTS chk_movement_type;

ALTER TABLE ${tenant_schema}.inventory_movement
    ADD CONSTRAINT chk_movement_type CHECK (movement_type IN
        ('PURCHASE','SALE','ADJUSTMENT','TRANSFER_IN','TRANSFER_OUT','RETURN','WASTE_OUT'));
