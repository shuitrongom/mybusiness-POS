-- =====================================================================
-- MyBusiness Silva — V42 (tenant): MERMA POR ENVEJECIMIENTO (pan del día
-- anterior) + TABLA GUARDIA bakery_aging_run.
--
-- Número de versión: V42. La más alta tenant es V41__sale_and_cut_test_flag.
--   No existe V42 => esta migración toma V42. (Reconfirmado listando el dir.)
--
-- Base de la automatización de merma del giro panadería (spec
-- merma-automatica-salidas):
--   1) Se amplía el CHECK de bakery_waste_movement.origin para admitir el nuevo
--      origen ENVEJECIMIENTO (pan del día anterior que, al cambiar el día natural
--      -cron/catch-up- o al pasar de modo prueba a real, se transfiere del stock
--      normal a merma). Los otros orígenes (CIERRE, MANUAL, VENTA, REPARTO) se
--      conservan intactos.
--   2) bakery_aging_run: tabla GUARDIA (idempotencia del envejecimiento). Una
--      fila por (sucursal, día natural) impide que el cron y el catch-up de
--      apertura de turno envejezcan el mismo día dos veces. El UNIQUE sirve de
--      candado a nivel BD.
--
-- Idempotente: DROP CONSTRAINT IF EXISTS + ADD, CREATE TABLE IF NOT EXISTS,
-- RLS con DROP POLICY IF EXISTS. Flyway sustituye ${tenant_schema}.
-- NO ejecutar aquí: Railway corre las migraciones Flyway al desplegar.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

-- ---------------------------------------------------------------------
-- 1) Ampliar el CHECK de origin para admitir ENVEJECIMIENTO.
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.bakery_waste_movement
    DROP CONSTRAINT IF EXISTS bakery_waste_movement_origin_check;

ALTER TABLE ${tenant_schema}.bakery_waste_movement
    ADD CONSTRAINT bakery_waste_movement_origin_check
    CHECK (origin IN ('CIERRE','MANUAL','VENTA','REPARTO','ENVEJECIMIENTO'));

-- ---------------------------------------------------------------------
-- 2) Tabla guardia del envejecimiento: una corrida por sucursal/día natural.
--    UNIQUE(branch_id, business_date) = candado de idempotencia (cron + catch-up).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.bakery_aging_run (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    branch_id     BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    business_date DATE          NOT NULL,
    pieces_aged   NUMERIC(14,3) NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_bakery_aging_run UNIQUE (branch_id, business_date)
);

-- ---------------------------------------------------------------------
-- 3) Row-Level Security de la tabla nueva (idempotente, igual que V36).
-- ---------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['bakery_aging_run']
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', '${tenant_schema}', t);
        EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', '${tenant_schema}', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation_%I ON %I.%I', t, '${tenant_schema}', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation_%I ON %I.%I USING (tenant_id = current_setting(''app.current_tenant'', true)) WITH CHECK (tenant_id = current_setting(''app.current_tenant'', true))',
            t, '${tenant_schema}', t);
    END LOOP;
END $$;
