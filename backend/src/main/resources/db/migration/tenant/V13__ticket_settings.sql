-- =====================================================================
-- MyBusiness Silva — V13 (tenant): configuración del ticket de venta.
--
-- Cada negocio personaliza qué aparece en su ticket impreso: nombre
-- comercial, dirección, teléfono, RFC, mensaje de encabezado y de pie, y
-- banderas para mostrar u ocultar elementos. Es una fila única por tenant
-- (singleton): se crea una fila por defecto al aprovisionar.
--
-- Flyway sustituye ${tenant_schema} por el schema destino. RLS por tenant.
-- =====================================================================

CREATE TABLE ${tenant_schema}.ticket_settings (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    business_name  VARCHAR(200),                 -- nombre comercial mostrado en el ticket
    address        VARCHAR(400),
    phone          VARCHAR(60),
    rfc            VARCHAR(13),
    header_message VARCHAR(300),                 -- mensaje bajo el nombre (ej. eslogan)
    footer_message VARCHAR(300) DEFAULT '¡Gracias por su compra!',
    show_logo      BOOLEAN      NOT NULL DEFAULT TRUE,
    show_address   BOOLEAN      NOT NULL DEFAULT TRUE,
    show_phone     BOOLEAN      NOT NULL DEFAULT TRUE,
    show_rfc       BOOLEAN      NOT NULL DEFAULT FALSE,
    show_cashier   BOOLEAN      NOT NULL DEFAULT TRUE,
    show_folio     BOOLEAN      NOT NULL DEFAULT TRUE,
    paper_width_mm INTEGER      NOT NULL DEFAULT 80,   -- 58 o 80 mm
    logo_url       TEXT,                              -- logo (data URL o URL)
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Fila única por tenant.
CREATE UNIQUE INDEX uq_ticket_settings_tenant ON ${tenant_schema}.ticket_settings (tenant_id);

-- Semilla: una fila por defecto para el tenant.
INSERT INTO ${tenant_schema}.ticket_settings (tenant_id, footer_message)
VALUES ('${tenant_schema}', '¡Gracias por su compra!');

ALTER TABLE ${tenant_schema}.ticket_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.ticket_settings FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_ticket_settings ON ${tenant_schema}.ticket_settings
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
