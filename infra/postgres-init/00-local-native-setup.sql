-- =====================================================================
-- Inicialización para PostgreSQL NATIVO en Windows (sin Docker).
-- Ejecutar UNA sola vez, conectado como el superusuario 'postgres':
--
--   "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -f infra\postgres-init\00-local-native-setup.sql
--
-- Crea el usuario de aplicación (pos_app) y la base de datos
-- 'mybusiness_silva' cuyo dueño es pos_app, de modo que Flyway (que se
-- conecta como pos_app) pueda crear el schema 'admin' y los schemas de
-- cada tenant. pos_app NO es superusuario ni tiene BYPASSRLS, para que
-- las políticas de Row-Level Security (multi-tenant) sí apliquen.
-- =====================================================================

-- Rol de aplicación (idempotente).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'pos_app') THEN
        CREATE ROLE pos_app WITH LOGIN PASSWORD 'pos_app_dev';
    END IF;
END
$$;

-- Base de datos de la aplicación, propiedad de pos_app.
-- (CREATE DATABASE no admite IF NOT EXISTS; si ya existe, ignora el error.)
SELECT 'CREATE DATABASE mybusiness_silva OWNER pos_app'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'mybusiness_silva')
\gexec

GRANT ALL PRIVILEGES ON DATABASE mybusiness_silva TO pos_app;
