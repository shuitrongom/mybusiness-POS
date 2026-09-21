-- =====================================================================
-- Impresión de tickets (por tenant).
--   - ticket_template: diseño configurable del ticket (encabezado, pie, opciones).
--   - printer_profile: perfil de impresora por sucursal/caja (conexión, ancho, cajón).
-- Con RLS por tenant.
-- =====================================================================

-- Plantilla de ticket (diseño configurable sin programar).
CREATE TABLE ${tenant_schema}.ticket_template (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    name          VARCHAR(120) NOT NULL,
    header_lines  JSONB        NOT NULL DEFAULT '[]'::jsonb,  -- líneas del encabezado (negocio, dirección)
    footer_lines  JSONB        NOT NULL DEFAULT '[]'::jsonb,  -- líneas del pie (gracias, leyendas)
    show_logo     BOOLEAN      NOT NULL DEFAULT FALSE,
    paper_width   INTEGER      NOT NULL DEFAULT 48,           -- caracteres por línea (58mm≈32, 80mm≈48)
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Perfil de impresora por sucursal/caja.
CREATE TABLE ${tenant_schema}.printer_profile (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    branch_id       BIGINT       REFERENCES ${tenant_schema}.branch(id),
    cash_register_id BIGINT      REFERENCES ${tenant_schema}.cash_register(id),
    name            VARCHAR(120) NOT NULL,
    connection_type VARCHAR(20)  NOT NULL DEFAULT 'USB',   -- USB, NETWORK, BLUETOOTH
    target          VARCHAR(200),                          -- nombre/IP/MAC según la conexión
    paper_width     INTEGER      NOT NULL DEFAULT 48,
    open_drawer     BOOLEAN      NOT NULL DEFAULT TRUE,
    auto_cut        BOOLEAN      NOT NULL DEFAULT TRUE,
    template_id     BIGINT       REFERENCES ${tenant_schema}.ticket_template(id),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_printer_conn CHECK (connection_type IN ('USB','NETWORK','BLUETOOTH'))
);

ALTER TABLE ${tenant_schema}.ticket_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.printer_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.ticket_template FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.printer_profile FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_ticket_template ON ${tenant_schema}.ticket_template
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_printer_profile ON ${tenant_schema}.printer_profile
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
