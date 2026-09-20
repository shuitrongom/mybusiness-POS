-- Inicialización de PostgreSQL para desarrollo local.
-- Se ejecuta una sola vez, al crear el volumen de datos por primera vez.
--
-- Crea el usuario de aplicación (pos_app) con el que se conecta el backend.
-- NOTA: este usuario NO es superusuario ni tiene BYPASSRLS, de modo que las
-- políticas de Row-Level Security (multi-tenant) sí apliquen sobre él.

CREATE ROLE pos_app WITH LOGIN PASSWORD 'pos_app_dev';

GRANT CONNECT ON DATABASE mybusiness_silva TO pos_app;

-- El schema 'admin' y los schemas por tenant los crea/gestiona Flyway y el
-- servicio de aprovisionamiento. Damos al usuario de app permiso para usarlos.
ALTER DEFAULT PRIVILEGES FOR ROLE pos_admin IN SCHEMA public
    GRANT ALL ON TABLES TO pos_app;
