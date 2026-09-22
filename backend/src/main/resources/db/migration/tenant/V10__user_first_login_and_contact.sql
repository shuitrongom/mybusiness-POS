-- =====================================================================
-- MyBusiness Silva — V10 (tenant): primer ingreso y contacto del usuario.
--
-- Añade a app_user:
--   must_change_password: obliga a cambiar la contraseña en el primer ingreso.
--   whatsapp: número de WhatsApp del usuario (contacto para enviar credenciales).
--
-- Flyway sustituye ${tenant_schema} por el schema destino.
-- =====================================================================

ALTER TABLE ${tenant_schema}.app_user
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ${tenant_schema}.app_user
    ADD COLUMN IF NOT EXISTS whatsapp VARCHAR(30);
