-- =====================================================================
-- Roles y permisos por negocio (por tenant).
--   role: roles del negocio (predefinidos + personalizados).
--   role_permission: permisos (módulo + acción) asignados a cada rol.
-- Cada usuario del negocio (app_user) puede tener un rol asignado.
-- Con RLS por tenant.
-- =====================================================================

CREATE TABLE ${tenant_schema}.role (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    code       VARCHAR(60)  NOT NULL,
    name       VARCHAR(120) NOT NULL,
    system_role BOOLEAN     NOT NULL DEFAULT FALSE,  -- true para roles predefinidos
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_role_code UNIQUE (code)
);

CREATE TABLE ${tenant_schema}.role_permission (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63) NOT NULL DEFAULT current_setting('app.current_tenant', true),
    role_id    BIGINT      NOT NULL REFERENCES ${tenant_schema}.role(id) ON DELETE CASCADE,
    module_key VARCHAR(60) NOT NULL,   -- módulo (sales, inventory, ...)
    action     VARCHAR(60) NOT NULL,   -- acción (VIEW, CREATE, VOID, ...)
    CONSTRAINT uq_role_permission UNIQUE (role_id, module_key, action)
);

-- Asignación de rol a los usuarios del negocio (columna en app_user).
ALTER TABLE ${tenant_schema}.app_user
    ADD COLUMN role_id BIGINT REFERENCES ${tenant_schema}.role(id);

-- Roles predefinidos del negocio.
-- Se fija tenant_id explícitamente al schema del tenant: durante la migración no hay
-- app.current_tenant en la sesión, por lo que no puede depender del valor por defecto.
INSERT INTO ${tenant_schema}.role (tenant_id, code, name, system_role) VALUES
    ('${tenant_schema}', 'OWNER',      'Dueño',        true),
    ('${tenant_schema}', 'ADMIN',      'Administrador', true),
    ('${tenant_schema}', 'SUPERVISOR', 'Supervisor',   true),
    ('${tenant_schema}', 'CASHIER',    'Cajero',       true);

-- RLS.
ALTER TABLE ${tenant_schema}.role            ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.role_permission ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.role            FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.role_permission FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_role ON ${tenant_schema}.role
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_role_permission ON ${tenant_schema}.role_permission
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
