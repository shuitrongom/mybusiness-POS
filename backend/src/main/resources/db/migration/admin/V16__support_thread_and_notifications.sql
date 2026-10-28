-- V16 — Hilo de conversación de soporte + Centro de notificaciones
CREATE TABLE IF NOT EXISTS admin.support_ticket_message (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id      UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    ticket_id      BIGINT        NOT NULL REFERENCES admin.support_ticket(id) ON DELETE CASCADE,
    author_type    VARCHAR(16)   NOT NULL,  -- SUPER_ADMIN | REPORTER
    author_role    VARCHAR(40),             -- rol fino: OWNER|ADMIN|SUPERVISOR|CASHIER|SUPER_ADMIN
    author_user_id VARCHAR(255)  NOT NULL,
    author_email   VARCHAR(255),
    body           VARCHAR(4000) NOT NULL,
    app_version    VARCHAR(60),
    screen_path    VARCHAR(300),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_msg_author_type CHECK (author_type IN ('SUPER_ADMIN','REPORTER'))
);
CREATE INDEX IF NOT EXISTS idx_support_msg_ticket ON admin.support_ticket_message (ticket_id, created_at);
CREATE INDEX IF NOT EXISTS idx_support_msg_public ON admin.support_ticket_message (public_id);

CREATE TABLE IF NOT EXISTS admin.notification (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID          NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    business_id       BIGINT        REFERENCES admin.business(id),
    tenant_schema     VARCHAR(63),
    recipient_user_id VARCHAR(255),
    recipient_scope   VARCHAR(16)   NOT NULL,  -- USER | SUPER_ADMIN
    type              VARCHAR(40)   NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    body              VARCHAR(600),
    ref_type          VARCHAR(40),
    ref_id            VARCHAR(120),
    payload           JSONB,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_notif_scope CHECK (recipient_scope IN ('USER','SUPER_ADMIN')),
    CONSTRAINT chk_notif_recipient CHECK (
        (recipient_scope = 'USER'        AND recipient_user_id IS NOT NULL AND business_id IS NOT NULL)
     OR (recipient_scope = 'SUPER_ADMIN' AND recipient_user_id IS NULL     AND business_id IS NULL)
    )
);
-- Índices parciales alineados al WHERE real de cada scope (M1):
CREATE INDEX IF NOT EXISTS idx_notif_user_unread
    ON admin.notification (business_id, recipient_user_id) WHERE read_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_notif_user_all
    ON admin.notification (business_id, recipient_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notif_superadmin_unread
    ON admin.notification (created_at DESC) WHERE read_at IS NULL AND recipient_scope = 'SUPER_ADMIN';
CREATE INDEX IF NOT EXISTS idx_notif_superadmin_all
    ON admin.notification (created_at DESC) WHERE recipient_scope = 'SUPER_ADMIN';

-- GRANTs defensivos (patrón idéntico a V15):
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_user}') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON admin.support_ticket_message TO ${app_user}';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON admin.notification TO ${app_user}';
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA admin TO ${app_user}';
    END IF;
END
$$;
