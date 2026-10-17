-- =====================================================================
-- MyBusiness Silva — V35 (tenant): PERMISOS RBAC DEL MÓDULO PANADERÍA.
--
-- Número de versión: V35.
--   La más alta tenant es V34__bakery_inventory.sql (este mismo feature).
--   No existe V35. => esta migración toma V35.
--
-- POR QUÉ EXISTE (cierre de hueco):
--   El módulo 'bakery' se habilita comercialmente por negocio (admin.business_module)
--   y su acceso efectivo se decide en ModuleAccessEvaluator.canUse('bakery'). Para
--   usuarios con matriz RBAC (role_permission, sembrada en V28) la decisión es
--   hasAnyPermissionOnModule('bakery'): si NINGÚN rol tiene permisos sobre 'bakery'
--   en la matriz, el backend responde 401/403 aunque el módulo esté habilitado.
--   V28 sembró la matriz ANTES de que 'bakery' existiera, por lo que sin esta
--   migración el Dueño/Admin/Cajero quedarían sin permiso sobre panadería en todos
--   los tenants (incluida producción). Esta migración siembra esos permisos para
--   que el gating funcione de raíz, sin tocar nada a mano.
--
-- Mismo patrón y acciones que V28. Idempotente: ON CONFLICT DO NOTHING.
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

-- OWNER y ADMIN: control total sobre panadería (igual que el resto de módulos).
INSERT INTO ${tenant_schema}.role_permission (tenant_id, role_id, module_key, action)
SELECT '${tenant_schema}', r.id, 'bakery', a.action
FROM ${tenant_schema}.role r
CROSS JOIN (VALUES
    ('VIEW'),('CREATE'),('EDIT'),('DELETE'),('VOID'),('EXPORT'),('MANAGE')
) AS a(action)
WHERE r.code IN ('OWNER','ADMIN')
ON CONFLICT (role_id, module_key, action) DO NOTHING;

-- SUPERVISOR: operación completa de panadería (ver, capturar producción, editar,
-- registrar merma y exportar), sin administración de la configuración del módulo.
INSERT INTO ${tenant_schema}.role_permission (tenant_id, role_id, module_key, action)
SELECT '${tenant_schema}', r.id, 'bakery', a.action
FROM ${tenant_schema}.role r
CROSS JOIN (VALUES ('VIEW'),('CREATE'),('EDIT'),('EXPORT')) AS a(action)
WHERE r.code = 'SUPERVISOR'
ON CONFLICT (role_id, module_key, action) DO NOTHING;

-- CASHIER: opera la panadería del día a día (consulta existencias, captura la
-- producción de pan y registra la merma vendida). Sin borrar ni administrar.
INSERT INTO ${tenant_schema}.role_permission (tenant_id, role_id, module_key, action)
SELECT '${tenant_schema}', r.id, 'bakery', a.action
FROM ${tenant_schema}.role r
CROSS JOIN (VALUES ('VIEW'),('CREATE'),('EDIT')) AS a(action)
WHERE r.code = 'CASHIER'
ON CONFLICT (role_id, module_key, action) DO NOTHING;
