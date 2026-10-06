-- =====================================================================
-- MyBusiness Silva — V27 (tenant): Facturación Electrónica CFDI 4.0 (suite).
--
-- Construye sobre V6 (tablas cfdi y cfdi_payment_complement). Añade:
--   - cfdi_issuer: datos fiscales del EMISOR (singleton) + config PAC/modo prueba.
--   - cfdi_series: series y folios por tipo de comprobante.
--   - cfdi_concept: conceptos persistidos por comprobante (antes solo se timbraban).
--   - remission: remisiones (notas de venta no fiscales) convertibles a factura.
--   - Catálogos Carta Porte 3.1: permiso, vehículo, remolque, operador, ubicación.
--   - Ampliación de cfdi: serie, folio, tipo de comprobante, forma/método de pago,
--     moneda, uuid relacionado, y complemento de carta porte.
--   - Ampliación de product y business_line con overrides de clave SAT.
--
-- Flyway sustituye ${tenant_schema}. Todo con RLS por tenant. Idempotente donde aplica.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Datos fiscales del EMISOR (singleton por tenant)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.cfdi_issuer (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    legal_name     VARCHAR(300),                 -- nombre/razón social del emisor (exacto)
    rfc            VARCHAR(13),
    tax_regime     VARCHAR(5),                   -- c_RegimenFiscal
    street         VARCHAR(200),
    ext_number     VARCHAR(30),
    int_number     VARCHAR(30),
    neighborhood   VARCHAR(120),                 -- colonia
    locality       VARCHAR(120),
    municipality   VARCHAR(120),
    state          VARCHAR(120),
    zip_code       VARCHAR(5),                   -- lugar de expedición (CP)
    -- Series por tipo (respaldo; el detalle fino vive en cfdi_series).
    series_invoice VARCHAR(25),
    series_credit  VARCHAR(25),                  -- notas de crédito
    series_payroll VARCHAR(25),                  -- nóminas
    series_payment VARCHAR(25),                  -- recibos de pago
    series_transfer VARCHAR(25),                 -- traslados
    -- Certificado de sello digital (para timbrado real; se cargan al contratar PAC).
    csd_cer_number VARCHAR(30),                  -- número de certificado
    csd_cer_path   VARCHAR(400),
    csd_key_path   VARCHAR(400),
    -- Configuración de timbrado.
    test_mode      BOOLEAN      NOT NULL DEFAULT TRUE,   -- modo prueba (sandbox)
    pac_provider   VARCHAR(40),                  -- proveedor PAC (Finkok, Facturama...)
    pac_user       VARCHAR(120),
    pac_ws_url     VARCHAR(300),
    decimals       SMALLINT     NOT NULL DEFAULT 2,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_cfdi_issuer_tenant ON ${tenant_schema}.cfdi_issuer (tenant_id);

-- Semilla singleton (toma datos del ticket_settings si existen).
INSERT INTO ${tenant_schema}.cfdi_issuer (tenant_id, legal_name, rfc, series_invoice, series_payment, test_mode)
SELECT '${tenant_schema}', ts.business_name, ts.rfc, 'A', 'P', TRUE
FROM ${tenant_schema}.ticket_settings ts
LIMIT 1
ON CONFLICT DO NOTHING;

-- Si no había ticket_settings, garantizar la fila.
INSERT INTO ${tenant_schema}.cfdi_issuer (tenant_id, series_invoice, series_payment, test_mode)
SELECT '${tenant_schema}', 'A', 'P', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ${tenant_schema}.cfdi_issuer);

-- ---------------------------------------------------------------------
-- 2) Series y folios por tipo de comprobante
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.cfdi_series (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    doc_type       VARCHAR(20)  NOT NULL DEFAULT 'INVOICE',  -- INVOICE, CREDIT, PAYMENT, PAYROLL, TRANSFER, GLOBAL
    series         VARCHAR(25)  NOT NULL,
    last_folio     BIGINT       NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_cfdi_series UNIQUE (doc_type, series)
);

INSERT INTO ${tenant_schema}.cfdi_series (tenant_id, doc_type, series, last_folio) VALUES
    ('${tenant_schema}', 'INVOICE', 'A', 0),
    ('${tenant_schema}', 'PAYMENT', 'P', 0),
    ('${tenant_schema}', 'GLOBAL',  'G', 0)
ON CONFLICT (doc_type, series) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3) Conceptos persistidos del CFDI
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.cfdi_concept (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    cfdi_id        BIGINT        NOT NULL REFERENCES ${tenant_schema}.cfdi(id) ON DELETE CASCADE,
    sat_prod_serv  VARCHAR(8)    NOT NULL,
    sat_unit       VARCHAR(3)    NOT NULL DEFAULT 'H87',
    description    VARCHAR(1000) NOT NULL,
    quantity       NUMERIC(14,6) NOT NULL DEFAULT 1,
    unit_price     NUMERIC(14,6) NOT NULL DEFAULT 0,
    amount         NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount       NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_object     VARCHAR(2)    NOT NULL DEFAULT '02',   -- ObjetoImp: 01 no objeto, 02 sí objeto
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cfdi_concept_cfdi ON ${tenant_schema}.cfdi_concept (cfdi_id);

-- ---------------------------------------------------------------------
-- 4) Ampliación de la tabla cfdi (serie, folio, tipo, forma/método de pago)
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.cfdi ADD COLUMN IF NOT EXISTS series         VARCHAR(25);
ALTER TABLE ${tenant_schema}.cfdi ADD COLUMN IF NOT EXISTS folio          BIGINT;
ALTER TABLE ${tenant_schema}.cfdi ADD COLUMN IF NOT EXISTS doc_type       VARCHAR(20)  NOT NULL DEFAULT 'INVOICE';
ALTER TABLE ${tenant_schema}.cfdi ADD COLUMN IF NOT EXISTS payment_form   VARCHAR(2);   -- c_FormaPago
ALTER TABLE ${tenant_schema}.cfdi ADD COLUMN IF NOT EXISTS payment_method VARCHAR(3)   DEFAULT 'PUE'; -- c_MetodoPago
ALTER TABLE ${tenant_schema}.cfdi ADD COLUMN IF NOT EXISTS currency       VARCHAR(3)   NOT NULL DEFAULT 'MXN';
ALTER TABLE ${tenant_schema}.cfdi ADD COLUMN IF NOT EXISTS exchange_rate  NUMERIC(14,6);
ALTER TABLE ${tenant_schema}.cfdi ADD COLUMN IF NOT EXISTS related_uuid   VARCHAR(40);  -- CFDI relacionado
ALTER TABLE ${tenant_schema}.cfdi ADD COLUMN IF NOT EXISTS relation_type  VARCHAR(2);   -- c_TipoRelacion
ALTER TABLE ${tenant_schema}.cfdi ADD COLUMN IF NOT EXISTS has_carta_porte BOOLEAN     NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------
-- 5) Ampliación del complemento de pago (Pagos 2.0)
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.cfdi_payment_complement ADD COLUMN IF NOT EXISTS customer_id BIGINT;
ALTER TABLE ${tenant_schema}.cfdi_payment_complement ADD COLUMN IF NOT EXISTS series       VARCHAR(25);
ALTER TABLE ${tenant_schema}.cfdi_payment_complement ADD COLUMN IF NOT EXISTS folio        BIGINT;
ALTER TABLE ${tenant_schema}.cfdi_payment_complement ADD COLUMN IF NOT EXISTS currency     VARCHAR(3) NOT NULL DEFAULT 'MXN';
ALTER TABLE ${tenant_schema}.cfdi_payment_complement ADD COLUMN IF NOT EXISTS exchange_rate NUMERIC(14,6);
ALTER TABLE ${tenant_schema}.cfdi_payment_complement ADD COLUMN IF NOT EXISTS operation_no VARCHAR(100);
ALTER TABLE ${tenant_schema}.cfdi_payment_complement ADD COLUMN IF NOT EXISTS bank         VARCHAR(120);

-- Documentos relacionados a un recibo de pago (doctos que se pagan con este complemento).
CREATE TABLE IF NOT EXISTS ${tenant_schema}.cfdi_payment_doc (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    complement_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.cfdi_payment_complement(id) ON DELETE CASCADE,
    related_cfdi_id BIGINT       REFERENCES ${tenant_schema}.cfdi(id),
    related_uuid   VARCHAR(40),
    installment    INTEGER       NOT NULL DEFAULT 1,   -- número de parcialidad
    prev_balance   NUMERIC(14,2) NOT NULL DEFAULT 0,
    paid_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    new_balance    NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cfdi_pay_doc_comp ON ${tenant_schema}.cfdi_payment_doc (complement_id);

-- ---------------------------------------------------------------------
-- 6) Remisiones (notas de venta no fiscales, convertibles a factura)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.remission (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    folio          VARCHAR(40),
    branch_id      BIGINT,
    customer_id    BIGINT,
    customer_name  VARCHAR(300),
    subtotal       NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax            NUMERIC(14,2) NOT NULL DEFAULT 0,
    total          NUMERIC(14,2) NOT NULL DEFAULT 0,
    status         VARCHAR(12)   NOT NULL DEFAULT 'OPEN',   -- OPEN, INVOICED, CANCELED
    invoiced_cfdi_id BIGINT      REFERENCES ${tenant_schema}.cfdi(id),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_remission_status CHECK (status IN ('OPEN','INVOICED','CANCELED'))
);

CREATE TABLE IF NOT EXISTS ${tenant_schema}.remission_line (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    remission_id   BIGINT        NOT NULL REFERENCES ${tenant_schema}.remission(id) ON DELETE CASCADE,
    product_id     BIGINT,
    description    VARCHAR(500)  NOT NULL,
    quantity       NUMERIC(14,3) NOT NULL DEFAULT 1,
    unit_price     NUMERIC(14,2) NOT NULL DEFAULT 0,
    amount         NUMERIC(14,2) NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_remission_line_rem ON ${tenant_schema}.remission_line (remission_id);
CREATE INDEX IF NOT EXISTS idx_remission_status   ON ${tenant_schema}.remission (status);

-- ---------------------------------------------------------------------
-- 7) Catálogos Carta Porte 3.1 (autotransporte)
-- ---------------------------------------------------------------------
-- Permiso SICT del transportista.
CREATE TABLE IF NOT EXISTS ${tenant_schema}.cp_permiso (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    tipo_permiso  VARCHAR(10)  NOT NULL,     -- c_TipoPermiso (TPAF01, etc.)
    numero        VARCHAR(50)  NOT NULL,     -- número del permiso SICT
    descripcion   VARCHAR(200),
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Vehículo (tractor / camión).
CREATE TABLE IF NOT EXISTS ${tenant_schema}.cp_vehiculo (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    config_vehic  VARCHAR(10)  NOT NULL,     -- c_ConfigAutotransporte (C2, C3, T3S2...)
    placa         VARCHAR(20)  NOT NULL,
    anio_modelo   INTEGER,
    aseguradora   VARCHAR(120),
    poliza_seguro VARCHAR(60),
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Remolque.
CREATE TABLE IF NOT EXISTS ${tenant_schema}.cp_remolque (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    subtipo       VARCHAR(10)  NOT NULL,     -- c_SubTipoRem (CTR001, etc.)
    placa         VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Operador (chofer).
CREATE TABLE IF NOT EXISTS ${tenant_schema}.cp_operador (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    nombre        VARCHAR(200) NOT NULL,
    rfc           VARCHAR(13),
    curp          VARCHAR(18),
    num_licencia  VARCHAR(30),
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Ubicaciones (origen / destino).
CREATE TABLE IF NOT EXISTS ${tenant_schema}.cp_ubicacion (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    tipo          VARCHAR(10)  NOT NULL DEFAULT 'Origen',   -- Origen / Destino
    nombre        VARCHAR(200),
    rfc           VARCHAR(13),
    calle         VARCHAR(200),
    num_ext       VARCHAR(30),
    colonia       VARCHAR(120),
    municipio     VARCHAR(120),
    estado        VARCHAR(120),
    pais          VARCHAR(60)  NOT NULL DEFAULT 'MEX',
    cp            VARCHAR(10),
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- 8) Overrides de clave SAT por producto y por línea de negocio
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS sat_tax_object VARCHAR(2)  DEFAULT '02'; -- ObjetoImp
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS sat_ret_iva    NUMERIC(6,4) DEFAULT 0;
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS sat_ret_isr    NUMERIC(6,4) DEFAULT 0;

-- Clave SAT por LÍNEA (categoría/línea del negocio) para asignar en bloque.
CREATE TABLE IF NOT EXISTS ${tenant_schema}.line_sat_key (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    line_name     VARCHAR(160) NOT NULL,       -- nombre de la línea/categoría
    sat_prod_serv VARCHAR(8)   NOT NULL,
    sat_unit      VARCHAR(3)   NOT NULL DEFAULT 'H87',
    tax_object    VARCHAR(2)   NOT NULL DEFAULT '02',
    CONSTRAINT uq_line_sat_key UNIQUE (line_name)
);

-- ---------------------------------------------------------------------
-- 9) Row-Level Security en TODAS las tablas nuevas
-- ---------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'cfdi_issuer','cfdi_series','cfdi_concept','cfdi_payment_doc',
        'remission','remission_line',
        'cp_permiso','cp_vehiculo','cp_remolque','cp_operador','cp_ubicacion',
        'line_sat_key'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', '${tenant_schema}', t);
        EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', '${tenant_schema}', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation_%s ON %I.%I '
            || 'USING (tenant_id = current_setting(''app.current_tenant'', true)) '
            || 'WITH CHECK (tenant_id = current_setting(''app.current_tenant'', true))',
            t, '${tenant_schema}', t);
    END LOOP;
END
$$;
