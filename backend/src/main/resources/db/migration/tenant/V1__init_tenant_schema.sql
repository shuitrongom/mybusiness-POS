-- =====================================================================
-- MyBusiness Silva — Migración base del schema de un negocio (tenant).
-- Se ejecuta sobre el schema tenant_<id> al aprovisionar un negocio.
--
-- Esta migración crea la estructura mínima común y demuestra el patrón de
-- aislamiento con Row-Level Security (RLS). Las tablas de cada módulo
-- (ventas, inventario, etc.) se añadirán en migraciones posteriores del
-- propio módulo, siguiendo este mismo patrón.
--
-- Flyway sustituye ${tenant_schema} por el schema destino al migrar.
-- Cada tabla lleva una columna tenant_id y una política RLS que solo
-- permite ver/tocar filas cuyo tenant_id coincide con app.current_tenant.
-- =====================================================================

-- Sucursales del negocio.
CREATE TABLE ${tenant_schema}.branch (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    name       VARCHAR(200) NOT NULL,
    code       VARCHAR(40),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Usuarios del negocio (operativos: dueño, admin, supervisor, cajero).
CREATE TABLE ${tenant_schema}.app_user (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

-- Auditoría del negocio (append-only).
CREATE TABLE ${tenant_schema}.audit_log (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    actor       VARCHAR(255),
    action      VARCHAR(120) NOT NULL,
    target_type VARCHAR(120),
    target_id   VARCHAR(120),
    details     JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Row-Level Security: segunda barrera de aislamiento.
-- Aunque el search_path ya apunta al schema del tenant, RLS garantiza que
-- ninguna fila de otro tenant sea visible ni modificable si por error una
-- consulta accediera a datos ajenos.
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.branch    ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.app_user  ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.audit_log ENABLE ROW LEVEL SECURITY;

ALTER TABLE ${tenant_schema}.branch    FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.app_user  FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.audit_log FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_branch ON ${tenant_schema}.branch
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY tenant_isolation_app_user ON ${tenant_schema}.app_user
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY tenant_isolation_audit_log ON ${tenant_schema}.audit_log
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
