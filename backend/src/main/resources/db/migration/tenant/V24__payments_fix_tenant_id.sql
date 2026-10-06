-- =====================================================================
-- MyBusiness Silva — V24 (tenant): CORRIGE tenant_id del catálogo de pagos.
--
-- CAUSA RAÍZ: las tablas de pagos (payment_catalog, payment_balance,
-- payment_deposit) tienen Row-Level Security con la política
--   tenant_id = current_setting('app.current_tenant', true)
-- y la columna tenant_id usa DEFAULT current_setting('app.current_tenant', true).
--
-- Durante las migraciones de Flyway (V22/V23) NO existe contexto de tenant, así
-- que 'app.current_tenant' es NULL y las filas se sembraron con tenant_id = NULL.
-- En tiempo de ejecución la petición SÍ fija app.current_tenant = '<schema>'
-- (p. ej. 'tenant_1'), por lo que la RLS ocultaba TODO el catálogo (NULL <> 'tenant_1')
-- y el frontend mostraba "Sin catálogo disponible".
--
-- SOLUCIÓN DEFINITIVA: fijar app.current_tenant al nombre del schema durante esta
-- migración (para que la RLS permita el UPDATE) y corregir tenant_id de todas las
-- filas al valor que la RLS espera en runtime: el nombre del schema del tenant.
--
-- Flyway sustituye ${tenant_schema} por el schema real (p. ej. tenant_1).
-- Idempotente: puede re-ejecutarse sin efectos adversos.
-- =====================================================================

-- Fija el tenant de la sesión de migración para satisfacer FORCE ROW LEVEL SECURITY.
SET app.current_tenant = '${tenant_schema}';

-- Realinea el tenant_id de todas las filas de pagos al identificador esperado por la RLS.
UPDATE ${tenant_schema}.payment_catalog
   SET tenant_id = '${tenant_schema}'
 WHERE tenant_id IS DISTINCT FROM '${tenant_schema}';

UPDATE ${tenant_schema}.payment_balance
   SET tenant_id = '${tenant_schema}'
 WHERE tenant_id IS DISTINCT FROM '${tenant_schema}';

UPDATE ${tenant_schema}.payment_deposit
   SET tenant_id = '${tenant_schema}'
 WHERE tenant_id IS DISTINCT FROM '${tenant_schema}';
