-- =====================================================================
-- MyBusiness Silva — V43 (tenant): REAPERTURA DE TURNO (OWNER-only).
--
-- Número de versión: V43. La más alta tenant es V42
--   (V42__bakery_aging_and_waste_origin.sql; el log de arranque confirma v42).
--   No existe V43 => esta migración toma V43.
--
-- Soporta reabrir un turno cerrado por error (solo el dueño), sin borrar el
-- corte Z previo: el Z se marca ANULADO POR REAPERTURA (voided=TRUE + quién/
-- cuándo/motivo), conservando su counted_cash como referencia. El turno guarda
-- rastro de reapertura (reopened_by/at/reason/count). Append-only; nada se borra.
--
-- Default FALSE/NULL/0: deja INTACTOS los cortes y turnos existentes (siguen
-- vigentes, no anulados, sin rastro de reapertura).
--
-- Idempotente: ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS. No altera
-- constraints con nombre (solo agrega columnas e índices; no hace falta resolver
-- nombres por catálogo). Se fija app.current_tenant por consistencia con V41/V42;
-- el DDL (ALTER/CREATE INDEX) NO está sujeto a RLS. Flyway sustituye
-- ${tenant_schema}. NO ejecutar aquí: Railway corre las migraciones al desplegar.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

-- ---------------------------------------------------------------------
-- 1) Anulación del corte (cash_cut) SIN borrarlo: quién/cuándo/motivo.
--    El counted_cash del corte anulado NO se toca (se conserva como
--    referencia para comparar contra el conteo del recierre).
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.cash_cut
    ADD COLUMN IF NOT EXISTS voided        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS voided_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS voided_by     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS voided_reason VARCHAR(300);

-- Índice parcial: localizar rápido el corte Z VIGENTE (no anulado) de un turno,
-- justo lo que consultan existingClosureOf / el recierre.
CREATE INDEX IF NOT EXISTS idx_cash_cut_shift_active
    ON ${tenant_schema}.cash_cut (shift_id, cut_type)
    WHERE voided = FALSE;

-- ---------------------------------------------------------------------
-- 2) Rastro de reapertura en el turno (shift): quién/cuándo/motivo y cuántas
--    veces se reabrió. No se toca el índice único parcial existente
--    uq_shift_open_per_register (cash_register_id) WHERE status='OPEN'; el
--    servicio garantiza no dejar dos turnos OPEN en la misma caja.
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.shift
    ADD COLUMN IF NOT EXISTS reopened_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reopened_by   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reopen_reason VARCHAR(300),
    ADD COLUMN IF NOT EXISTS reopen_count  INTEGER NOT NULL DEFAULT 0;
