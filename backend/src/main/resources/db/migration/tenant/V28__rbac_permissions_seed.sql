-- =====================================================================
-- MyBusiness Silva — V28 (tenant): RBAC configurable de verdad.
--
-- Hasta V9 existían las tablas role/role_permission pero role_permission NUNCA
-- se leía en runtime (la autorización estaba hardcodeada por rol). Esta
-- migración:
--   1) Añade límites por rol (descuento máx, si puede autorizar) y el PIN de
--      supervisor por usuario (para autorizar acciones sensibles en el POS).
--   2) SIEMBRA los permisos por defecto (module_key + action) de los 4 roles de
--      sistema, para que el motor de permisos ya tenga una matriz real que leer.
--
-- Fija app.current_tenant para satisfacer la RLS (FORCE) durante los INSERT/UPDATE.
-- Idempotente. Flyway sustituye ${tenant_schema}.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

-- ---------------------------------------------------------------------
-- 1) Límites por rol y PIN de supervisor
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.role ADD COLUMN IF NOT EXISTS max_discount_pct NUMERIC(5,2) NOT NULL DEFAULT 0;   -- % de descuento máximo que el rol puede aplicar sin autorización
ALTER TABLE ${tenant_schema}.role ADD COLUMN IF NOT EXISTS can_authorize   BOOLEAN      NOT NULL DEFAULT FALSE; -- si el rol puede autorizar acciones sensibles (supervisor)

-- PIN corto para autorizar en el POS (hash). Separado de la contraseña de acceso.
ALTER TABLE ${tenant_schema}.app_user ADD COLUMN IF NOT EXISTS supervisor_pin_hash VARCHAR(120);

-- Límites por defecto de los roles de sistema.
UPDATE ${tenant_schema}.role SET max_discount_pct = 100, can_authorize = TRUE  WHERE code IN ('OWNER','ADMIN');
UPDATE ${tenant_schema}.role SET max_discount_pct = 50,  can_authorize = TRUE  WHERE code = 'SUPERVISOR';
UPDATE ${tenant_schema}.role SET max_discount_pct = 5,   can_authorize = FALSE WHERE code = 'CASHIER';

-- ---------------------------------------------------------------------
-- 2) Semilla de permisos por rol (module_key + action)
--    Acciones: VIEW, CREATE, EDIT, DELETE, VOID (cancelar), DISCOUNT,
--    RETURN (devolver), AUTHORIZE (autorizar), EXPORT, MANAGE.
-- ---------------------------------------------------------------------

-- OWNER y ADMIN: acceso total (VIEW+MANAGE sobre todos los módulos).
INSERT INTO ${tenant_schema}.role_permission (tenant_id, role_id, module_key, action)
SELECT '${tenant_schema}', r.id, m.module_key, a.action
FROM ${tenant_schema}.role r
CROSS JOIN (VALUES
    ('sales'),('cash'),('inventory'),('purchasing'),('customers'),
    ('invoicing'),('payments'),('promotions'),('reports'),('bi'),
    ('multibranch'),('roles'),('printing'),('settings')
) AS m(module_key)
CROSS JOIN (VALUES
    ('VIEW'),('CREATE'),('EDIT'),('DELETE'),('VOID'),('DISCOUNT'),
    ('RETURN'),('AUTHORIZE'),('EXPORT'),('MANAGE')
) AS a(action)
WHERE r.code IN ('OWNER','ADMIN')
ON CONFLICT (role_id, module_key, action) DO NOTHING;

-- SUPERVISOR: operación completa (sin administrar usuarios/roles), puede autorizar,
-- cancelar, devolver y dar descuentos.
INSERT INTO ${tenant_schema}.role_permission (tenant_id, role_id, module_key, action)
SELECT '${tenant_schema}', r.id, m.module_key, a.action
FROM ${tenant_schema}.role r
CROSS JOIN (VALUES
    ('sales'),('cash'),('inventory'),('purchasing'),('customers'),
    ('invoicing'),('payments'),('promotions'),('reports'),('bi'),('printing')
) AS m(module_key)
CROSS JOIN (VALUES
    ('VIEW'),('CREATE'),('EDIT'),('VOID'),('DISCOUNT'),('RETURN'),('AUTHORIZE'),('EXPORT')
) AS a(action)
WHERE r.code = 'SUPERVISOR'
ON CONFLICT (role_id, module_key, action) DO NOTHING;

-- CASHIER: vender, manejar su caja, reimprimir, leer catálogo/clientes. SIN cancelar,
-- SIN devolver, SIN descuentos grandes (los pide con autorización de supervisor).
INSERT INTO ${tenant_schema}.role_permission (tenant_id, role_id, module_key, action)
SELECT '${tenant_schema}', r.id, p.module_key, p.action
FROM ${tenant_schema}.role r
CROSS JOIN (VALUES
    ('sales','VIEW'),('sales','CREATE'),('sales','DISCOUNT'),
    ('cash','VIEW'),('cash','CREATE'),
    ('printing','VIEW'),('printing','CREATE'),
    ('customers','VIEW'),
    ('inventory','VIEW')
) AS p(module_key, action)
WHERE r.code = 'CASHIER'
ON CONFLICT (role_id, module_key, action) DO NOTHING;
