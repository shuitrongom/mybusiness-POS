-- V17 — Fase 2: email INBOUND → hilo del ticket.
-- Tabla de deduplicación de eventos inbound (idempotencia del webhook de Resend) + marca de origen
-- del mensaje ("llegó por correo"). ADITIVO: no toca el panel de Fase 1.

-- Tabla de deduplicación/traza de eventos inbound (§7.1 del diseño).
CREATE TABLE IF NOT EXISTS admin.support_inbound_event (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id      VARCHAR(255) NOT NULL UNIQUE,          -- svix-id (o 'sha256:<hex>' del rawBody si faltara)
    ticket_id     BIGINT       REFERENCES admin.support_ticket(id) ON DELETE SET NULL,
    message_id    BIGINT       REFERENCES admin.support_ticket_message(id) ON DELETE SET NULL,
    sender_email  VARCHAR(255),
    outcome       VARCHAR(24)  NOT NULL,                 -- PROCESSING (intermedio) | INSERTED |
                                                         -- DISCARDED_ALLOWLIST | DISCARDED_NO_TICKET |
                                                         -- DISCARDED_EMPTY | DISCARDED_NO_TOKEN
    reason        VARCHAR(300),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_inbound_outcome CHECK (outcome IN
        ('PROCESSING','INSERTED','DISCARDED_ALLOWLIST','DISCARDED_NO_TICKET','DISCARDED_EMPTY','DISCARDED_NO_TOKEN'))
);
CREATE INDEX IF NOT EXISTS idx_inbound_event_created ON admin.support_inbound_event (created_at DESC);

-- Marca de origen del mensaje del hilo (D-UI-2 = sí): 'PANEL' (default, Fase 1) | 'INBOUND_EMAIL'
-- (respuesta del equipo que llegó por correo, Fase 2). Default 'PANEL' => los mensajes existentes
-- no cambian de comportamiento.
ALTER TABLE admin.support_ticket_message
    ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'PANEL';
ALTER TABLE admin.support_ticket_message
    DROP CONSTRAINT IF EXISTS chk_msg_source;
ALTER TABLE admin.support_ticket_message
    ADD CONSTRAINT chk_msg_source CHECK (source IN ('PANEL','INBOUND_EMAIL'));

-- GRANTs defensivos (patrón idéntico a V15/V16):
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_user}') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON admin.support_inbound_event TO ${app_user}';
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA admin TO ${app_user}';
    END IF;
END
$$;
