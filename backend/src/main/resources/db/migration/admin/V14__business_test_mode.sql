-- =====================================================================
-- MyBusiness Silva — V14 (admin): FLAG DE MODO PRUEBA por negocio.
--
-- Número de versión: V14. La más alta admin real es V13
--   (V13__bakery_module.sql). No existe V14. => esta migración toma V14.
--
-- Añade admin.business.test_mode: mientras TRUE, las ventas nuevas del negocio
-- nacen is_test=true (ventas de PRÁCTICA, Opción B: se conservan como histórico;
-- NO se borran ni se resetea inventario/cortes). Lo controla SOLO el DUEÑO
-- (rol OWNER) desde la configuración del negocio. Un request de tenant puede
-- leer esta tabla porque TenantAwareDataSource fija search_path = <tenant>, admin;
-- se lee por schema_name = tenant en curso.
--
-- Default FALSE: inocuo para negocios existentes (siguen operando en real).
-- Idempotente: ADD COLUMN IF NOT EXISTS.
-- =====================================================================

ALTER TABLE admin.business
    ADD COLUMN IF NOT EXISTS test_mode BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN admin.business.test_mode IS
    'Modo prueba del negocio: mientras TRUE, las ventas nuevas nacen is_test=true. Lo controla solo el DUEÑO (rol OWNER).';
