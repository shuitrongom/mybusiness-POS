-- =====================================================================
-- MyBusiness Silva — Migración inicial del schema global de administración
-- Schema: admin
-- Contiene los datos globales del SaaS: negocios (tenants), planes,
-- módulos y usuarios del Super Admin. Los datos operativos de cada
-- negocio viven en su propio schema tenant_<id> (creado al aprovisionar).
-- =====================================================================

-- Catálogo de módulos comercializables del sistema.
CREATE TABLE admin.module_catalog (
    module_key                VARCHAR(60)   PRIMARY KEY,
    name                      VARCHAR(120)  NOT NULL,
    description               VARCHAR(500),
    surcharge_suggested_price NUMERIC(12,2) NOT NULL DEFAULT 0,
    active                    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE admin.module_catalog IS 'Módulos que el Super Admin puede vender/habilitar por negocio.';

-- Planes comerciales (paquetes de módulos con precio sugerido de licencia definitiva).
CREATE TABLE admin.plan (
    id                     BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                   VARCHAR(60)   NOT NULL UNIQUE,
    name                   VARCHAR(120)  NOT NULL,
    description            VARCHAR(500),
    license_price_suggested NUMERIC(12,2) NOT NULL DEFAULT 0,
    active                 BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE admin.plan IS 'Planes prearmados (paquetes de módulos) con precio de licencia sugerido.';

-- Módulos incluidos en cada plan.
CREATE TABLE admin.plan_module (
    plan_id    BIGINT      NOT NULL REFERENCES admin.plan(id) ON DELETE CASCADE,
    module_key VARCHAR(60) NOT NULL REFERENCES admin.module_catalog(module_key),
    PRIMARY KEY (plan_id, module_key)
);

-- Negocios (tenants). Cada uno tiene un schema propio (schema_name).
CREATE TABLE admin.business (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    name            VARCHAR(200)  NOT NULL,
    rfc             VARCHAR(13),
    business_line   VARCHAR(60),                 -- giro: abarrotes, panaderia, polleria, etc.
    schema_name     VARCHAR(63)   NOT NULL UNIQUE,
    subdomain       VARCHAR(100)  UNIQUE,
    -- Estado del ciclo de licencia:
    -- TRIAL (en prueba), ACTIVE (licencia comprada), SUSPENDED (suspendido), EXPIRED (prueba vencida).
    status          VARCHAR(20)   NOT NULL DEFAULT 'TRIAL',
    -- Conexión: DEFAULT (base compartida) o DEDICATED (base dedicada del cliente).
    connection_kind VARCHAR(20)   NOT NULL DEFAULT 'DEFAULT',
    trial_months    INTEGER       NOT NULL DEFAULT 1,
    trial_starts_at TIMESTAMPTZ,
    trial_ends_at   TIMESTAMPTZ,
    purchased_at    TIMESTAMPTZ,
    plan_id         BIGINT        REFERENCES admin.plan(id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_business_status
        CHECK (status IN ('TRIAL','ACTIVE','SUSPENDED','EXPIRED')),
    CONSTRAINT chk_business_connection_kind
        CHECK (connection_kind IN ('DEFAULT','DEDICATED'))
);

COMMENT ON TABLE admin.business IS 'Negocios (tenants) del SaaS. schema_name apunta a su schema de datos.';

-- Módulos habilitados por negocio (venta por módulos: plan o excedente).
CREATE TABLE admin.business_module (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id BIGINT        NOT NULL REFERENCES admin.business(id) ON DELETE CASCADE,
    module_key  VARCHAR(60)   NOT NULL REFERENCES admin.module_catalog(module_key),
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    origin      VARCHAR(20)   NOT NULL DEFAULT 'PLAN',  -- PLAN o SURCHARGE (excedente)
    sold_price  NUMERIC(12,2),
    sold_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_module UNIQUE (business_id, module_key),
    CONSTRAINT chk_business_module_origin CHECK (origin IN ('PLAN','SURCHARGE'))
);

COMMENT ON TABLE admin.business_module IS 'Módulos habilitados comercialmente por negocio (control del Super Admin).';

-- Usuarios del Super Admin (proveedor del SaaS).
CREATE TABLE admin.superadmin_user (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,      -- Argon2id
    full_name     VARCHAR(200) NOT NULL,
    mfa_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_secret    VARCHAR(255),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE admin.superadmin_user IS 'Usuarios administradores globales del SaaS.';

-- Ventas del Super Admin (licencias y excedentes) con su tipo de comprobante.
CREATE TABLE admin.superadmin_sale (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id    BIGINT        NOT NULL REFERENCES admin.business(id),
    kind           VARCHAR(20)   NOT NULL,                 -- LICENSE o SURCHARGE
    amount         NUMERIC(12,2) NOT NULL,
    voucher_type   VARCHAR(10)   NOT NULL,                 -- CFDI o PDF
    customer_email VARCHAR(255),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sale_kind CHECK (kind IN ('LICENSE','SURCHARGE')),
    CONSTRAINT chk_sale_voucher CHECK (voucher_type IN ('CFDI','PDF'))
);

COMMENT ON TABLE admin.superadmin_sale IS 'Registro de ventas del Super Admin (licencias y módulos adicionales).';

-- Auditoría global (acciones del Super Admin y del sistema a nivel plataforma).
CREATE TABLE admin.audit_log_global (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor       VARCHAR(255),
    action      VARCHAR(120) NOT NULL,
    target_type VARCHAR(120),
    target_id   VARCHAR(120),
    details     JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE admin.audit_log_global IS 'Bitácora de auditoría a nivel plataforma (append-only).';

CREATE INDEX idx_business_status   ON admin.business (status);
CREATE INDEX idx_business_plan     ON admin.business (plan_id);
CREATE INDEX idx_bmodule_business  ON admin.business_module (business_id);
CREATE INDEX idx_sale_business     ON admin.superadmin_sale (business_id);
CREATE INDEX idx_audit_global_time ON admin.audit_log_global (created_at);
