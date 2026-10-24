-- =====================================================================
-- MyBusiness Silva — V44 (tenant): FOLIO SERIE 'M-' PARA MOVIMIENTOS A MERMA.
--
-- Número de versión: V44. La más alta tenant aplicada es V43 (shift reopen),
--   confirmado por el log de arranque (schema tenant_9 @ v43). No existe V44.
--
-- Añade bakery_waste_movement.folio para que TODO movimiento a merma tenga un
-- folio propio de serie 'M-' (M-1, M-2, ...), estable y único, derivado del id
-- IDENTITY de la tabla (mismo patrón que PROD-/S-/E-/IF-). Es ADITIVO y metadato:
-- no cambia cantidades (quantity), ni la pérdida (difference), ni los saldos de
-- inventory_stock / bakery_waste_stock, ni las ecuaciones de conservación.
--
-- Históricos: las filas previas quedan con folio NULL (sin data-fix). La app
-- asigna 'M-'||id solo a los movimientos NUEVOS; el front muestra '—' para los
-- históricos sin folio. Ver design.md §6.
--
-- No se crea índice sobre folio: ninguna consulta filtra por folio (se lee junto
-- con la fila). Evitar esquema muerto (steering). Ver design.md §1.
--
-- Idempotente: ADD COLUMN IF NOT EXISTS. El DDL no está sujeto a RLS; se fija
-- app.current_tenant por consistencia con V41/V42/V43. Flyway sustituye
-- ${tenant_schema}. NO ejecutar a mano: Flyway corre al desplegar.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

-- ---------------------------------------------------------------------
-- Folio de serie 'M-' para la trazabilidad de merma. Nullable: los
-- movimientos históricos (anteriores a esta feature) quedan en NULL.
-- VARCHAR(40) = mismo tipo que inventory_document.folio y bakery_production.folio.
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.bakery_waste_movement
    ADD COLUMN IF NOT EXISTS folio VARCHAR(40);
