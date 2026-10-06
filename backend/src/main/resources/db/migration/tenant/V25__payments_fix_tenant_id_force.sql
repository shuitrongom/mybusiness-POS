-- =====================================================================
-- MyBusiness Silva — V25 (tenant): corrige tenant_id del catálogo de pagos
-- DESACTIVANDO temporalmente Row-Level Security.
--
-- Por qué V24 no bastó: las tablas de pagos tienen FORCE ROW LEVEL SECURITY,
-- de modo que la política aplica incluso al owner. Las filas sembradas con
-- tenant_id = NULL eran INVISIBLES para el UPDATE (la cláusula USING
-- tenant_id = 'tenant_1' no las selecciona), así que V24 actualizó 0 filas.
--
-- Aquí desactivamos RLS en las tres tablas, corregimos tenant_id de TODAS las
-- filas al nombre del schema (lo que la RLS espera en runtime) y volvemos a
-- activar RLS. Es la corrección definitiva e idempotente.
--
-- Flyway sustituye ${tenant_schema} por el schema real (p. ej. tenant_1).
-- =====================================================================

-- 1) Desactiva la seguridad por fila para poder ver y corregir TODAS las filas.
ALTER TABLE ${tenant_schema}.payment_catalog  DISABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_catalog  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_balance  DISABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_balance  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_deposit  DISABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_deposit  NO FORCE ROW LEVEL SECURITY;

-- 2) Realinea el tenant_id de TODAS las filas al identificador esperado por la RLS.
UPDATE ${tenant_schema}.payment_catalog SET tenant_id = '${tenant_schema}';
UPDATE ${tenant_schema}.payment_balance SET tenant_id = '${tenant_schema}';
UPDATE ${tenant_schema}.payment_deposit SET tenant_id = '${tenant_schema}';

-- 3) Reactiva la seguridad por fila (mismo estado que dejó V22).
ALTER TABLE ${tenant_schema}.payment_catalog  ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_catalog  FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_balance  ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_balance  FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_deposit  ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_deposit  FORCE ROW LEVEL SECURITY;
