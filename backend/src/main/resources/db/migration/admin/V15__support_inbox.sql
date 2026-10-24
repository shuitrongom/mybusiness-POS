-- =====================================================================
-- V15 — Buzón de Soporte / Feedback in-app (schema admin, transversal al SaaS)
-- Dato de plataforma: el Super Admin ve tickets de TODOS los negocios.
-- No usa RLS (patrón del schema admin). El aislamiento por negocio se
-- aplica en la capa de aplicación (WHERE business_id = ...).
-- =====================================================================

-- Ticket de soporte / feedback. Un ticket por reporte (ERROR o MEJORA).
CREATE TABLE IF NOT EXISTS admin.support_ticket (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        UUID         NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    business_id      BIGINT       REFERENCES admin.business(id),  -- nulo solo si no se resolvió el negocio
    tenant_schema    VARCHAR(63)  NOT NULL,                       -- schema de origen (tenant_9, etc.)
    reporter_user_id VARCHAR(255) NOT NULL,                       -- subject del token (correo/id)
    reporter_email   VARCHAR(255),
    reporter_role    VARCHAR(40)  NOT NULL,                       -- OWNER | CASHIER | ADMIN | ...
    type             VARCHAR(10)  NOT NULL,                       -- ERROR | MEJORA
    title            VARCHAR(160) NOT NULL,
    description      VARCHAR(4000) NOT NULL,
    screen_path      VARCHAR(300),                                -- ruta del front (/pos, /inventory-exits, ...)
    app_version      VARCHAR(60),                                 -- versión del front enviada por el cliente
    user_agent       VARCHAR(500),                                -- User-Agent del request
    device           VARCHAR(20)  NOT NULL DEFAULT 'unknown',     -- mobile | desktop | unknown
    status           VARCHAR(20)  NOT NULL DEFAULT 'NUEVO',       -- NUEVO | EN_REVISION | RESUELTO | CERRADO | DESCARTADO
    resolution_note  VARCHAR(2000),                               -- nota opcional al cambiar a RESUELTO/DESCARTADO
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ,
    resolved_by      VARCHAR(255),
    CONSTRAINT chk_support_type   CHECK (type   IN ('ERROR','MEJORA')),
    CONSTRAINT chk_support_status CHECK (status IN ('NUEVO','EN_REVISION','RESUELTO','CERRADO','DESCARTADO'))
);

COMMENT ON TABLE admin.support_ticket IS 'Tickets de soporte/feedback in-app (transversal al SaaS, visibles al Super Admin).';

-- Imágenes adjuntas al ticket (solo URL/public_id de Cloudinary; NUNCA el binario en Neon).
CREATE TABLE IF NOT EXISTS admin.support_ticket_image (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id     BIGINT       NOT NULL REFERENCES admin.support_ticket(id) ON DELETE CASCADE,
    cloudinary_url VARCHAR(600) NOT NULL,
    public_id     VARCHAR(300) NOT NULL,
    width         INTEGER,
    height        INTEGER,
    bytes         BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE admin.support_ticket_image IS 'Imágenes de un ticket de soporte (URL/public_id de Cloudinary).';

CREATE INDEX IF NOT EXISTS idx_support_ticket_business ON admin.support_ticket (business_id);
CREATE INDEX IF NOT EXISTS idx_support_ticket_status   ON admin.support_ticket (status);
CREATE INDEX IF NOT EXISTS idx_support_ticket_type     ON admin.support_ticket (type);
CREATE INDEX IF NOT EXISTS idx_support_ticket_created  ON admin.support_ticket (created_at);
CREATE INDEX IF NOT EXISTS idx_support_ticket_reporter ON admin.support_ticket (reporter_user_id);
CREATE INDEX IF NOT EXISTS idx_support_image_ticket    ON admin.support_ticket_image (ticket_id);

-- Permisos para el rol de aplicación (mismo patrón que V1; explícito y robusto).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_user}') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON admin.support_ticket TO ${app_user}';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON admin.support_ticket_image TO ${app_user}';
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA admin TO ${app_user}';
    END IF;
END
$$;
