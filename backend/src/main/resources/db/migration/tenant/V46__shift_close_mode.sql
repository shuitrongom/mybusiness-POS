-- =====================================================================
-- V46 (tenant): persistir el modo de cierre del turno (Opción B: relevo vs fin de día).
--   Aditiva e idempotente. close_mode NULL = turno no cerrado, o cerrado antes
--   de V46 (legado). En los chequeos de bloqueo diario, NULL legado se trata
--   como FIN_DIA (COALESCE), para NO aflojar la seguridad sobre datos históricos.
--   No toca V42/V43/V44/V45 ni el candado de merma. Flyway sustituye
--   ${tenant_schema} y corre al desplegar (no ejecutar a mano).
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

ALTER TABLE ${tenant_schema}.shift
    ADD COLUMN IF NOT EXISTS close_mode VARCHAR(16);

-- Índice parcial para el bloqueo diario. El predicado ESPEJA la consulta de §5.2
-- (COALESCE(close_mode,'FIN_DIA')='FIN_DIA'), es decir incluye los NULL legado,
-- para que el planner SÍ pueda usarlo en cashierClosedToday / hasClosedTodayOnRegister.
CREATE INDEX IF NOT EXISTS idx_shift_findia_closed
    ON ${tenant_schema}.shift (opened_by, cash_register_id, business_date)
    WHERE status = 'CLOSED' AND (close_mode = 'FIN_DIA' OR close_mode IS NULL);
