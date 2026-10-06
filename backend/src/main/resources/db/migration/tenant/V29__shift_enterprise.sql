-- =====================================================================
-- MyBusiness Silva — V29 (tenant): flujo de caja enterprise del cajero.
--
-- Refuerza los turnos de caja para el flujo real del punto de venta:
--   • Un solo turno ABIERTO por caja a la vez (índice único parcial).
--   • Registro de la sucursal y del cajero de apertura en el turno.
--   • Fecha de negocio del turno (para la regla "una vez cerrada la caja no se
--     puede volver a vender ese día").
--   • Entrega de caja (handover): de quién se recibe la caja al cambiar de cajero.
--
-- Fija app.current_tenant para la RLS. Idempotente. Flyway sustituye ${tenant_schema}.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

-- Sucursal y fecha de negocio del turno (para agrupar y para el bloqueo por día).
ALTER TABLE ${tenant_schema}.shift ADD COLUMN IF NOT EXISTS branch_id     BIGINT;
ALTER TABLE ${tenant_schema}.shift ADD COLUMN IF NOT EXISTS business_date DATE NOT NULL DEFAULT CURRENT_DATE;
-- Entrega de caja: turno del que se recibe (cambio de cajero) y notas.
ALTER TABLE ${tenant_schema}.shift ADD COLUMN IF NOT EXISTS handover_from BIGINT;
ALTER TABLE ${tenant_schema}.shift ADD COLUMN IF NOT EXISTS notes         VARCHAR(400);

-- Un solo turno ABIERTO por caja: evita dos cajeros abriendo la misma caja.
CREATE UNIQUE INDEX IF NOT EXISTS uq_shift_open_per_register
    ON ${tenant_schema}.shift (cash_register_id)
    WHERE status = 'OPEN';

-- Índice para consultar el turno activo de un cajero y las ventas por cajero.
CREATE INDEX IF NOT EXISTS idx_shift_opened_by ON ${tenant_schema}.shift (opened_by, status);
CREATE INDEX IF NOT EXISTS idx_shift_business_date ON ${tenant_schema}.shift (business_date);
CREATE INDEX IF NOT EXISTS idx_sale_cashier ON ${tenant_schema}.sale (cashier);
