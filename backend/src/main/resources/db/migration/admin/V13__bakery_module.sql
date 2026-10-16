-- =====================================================================
-- MyBusiness Silva — V13 (admin): MÓDULO COMERCIAL DE PANADERÍA.
--
-- Número de versión: V13.
--   Calculado contra el estado real del worktree v3 (= master). Máximo admin
--   real observado al listar el directorio de migraciones: V12
--   (V12__sat_catalogs_cfdi40.sql). No existe V13. => esta migración toma V13.
--
-- Habilita 'bakery' como módulo comercializable (el Super Admin lo activa por
-- negocio) y lo sugiere por defecto al giro 'panaderia'. El aislamiento por
-- giro se consigue reutilizando la cadena probada business_module → claim
-- modules → @moduleAccess.canUse('bakery') → hasModule('bakery') en el front.
--
-- Idempotente: INSERT ... ON CONFLICT DO NOTHING.
-- =====================================================================

INSERT INTO admin.module_catalog (module_key, name, description, surcharge_suggested_price)
VALUES ('bakery', 'Panadería', 'Producción de pan por día, no-sobreventa y merma vendible.', 800)
ON CONFLICT (module_key) DO NOTHING;

INSERT INTO admin.business_line_module (line_code, module_key)
VALUES ('panaderia', 'bakery')
ON CONFLICT DO NOTHING;
