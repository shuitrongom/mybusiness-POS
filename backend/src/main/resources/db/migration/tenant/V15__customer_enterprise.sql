-- =====================================================================
-- MyBusiness Silva — V15 (tenant): CLIENTES nivel ENTERPRISE.
--
-- Bloque 2 de la Suite de Ventas. Amplía el cliente con los datos que exige
-- una facturación CFDI 4.0 correcta ante el SAT y la operación comercial
-- profesional:
--   • Datos fiscales CFDI 4.0: razón social, tipo de persona, régimen fiscal,
--     uso de CFDI y código postal del domicilio fiscal (todos obligatorios en
--     4.0 para timbrar al receptor).
--   • Datos comerciales: contacto, vendedor asignado, días de crédito, foto,
--     notas y clasificación.
--   • Direcciones múltiples (fiscal, de envío, sucursales del cliente).
--
-- Idempotente (IF NOT EXISTS) y con defaults que no rompen los clientes ya
-- existentes. Flyway sustituye ${tenant_schema}.
-- =====================================================================

-- ---- Datos fiscales CFDI 4.0 ----
-- Tipo de persona: FISICA / MORAL (afecta el largo válido del RFC).
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS person_type    VARCHAR(10)  NOT NULL DEFAULT 'FISICA';
-- Razón social / nombre fiscal EXACTO como aparece en la Constancia de Situación Fiscal.
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS legal_name     VARCHAR(300);
-- Régimen fiscal (clave del catálogo c_RegimenFiscal del SAT, p.ej. 601, 612, 616).
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS tax_regime     VARCHAR(5);
-- Uso de CFDI por defecto (clave del catálogo c_UsoCFDI, p.ej. G03, G01, S01).
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS cfdi_use       VARCHAR(5)   DEFAULT 'G03';
-- Código postal del domicilio fiscal (obligatorio en CFDI 4.0).
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS zip_code       VARCHAR(10);

-- ---- Datos comerciales ----
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS contact_name   VARCHAR(200);
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS mobile         VARCHAR(40);
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS salesperson    VARCHAR(255);   -- vendedor asignado
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS credit_days    INTEGER      NOT NULL DEFAULT 0;   -- plazo de crédito en días
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS default_price_list SMALLINT  NOT NULL DEFAULT 1;   -- lista de precio del cliente
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS classification VARCHAR(40);    -- segmento (VIP, mayorista, etc.)
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS notes          TEXT;
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS image_url      TEXT;           -- foto/logo (URL o data URL)
ALTER TABLE ${tenant_schema}.customer ADD COLUMN IF NOT EXISTS external_code  VARCHAR(60);    -- código del cliente (interno del negocio)

-- Al migrar, si no hay razón social, se usa el nombre comercial como respaldo.
UPDATE ${tenant_schema}.customer SET legal_name = name WHERE legal_name IS NULL;

-- ---- Direcciones del cliente (fiscal, envío, sucursales) ----
CREATE TABLE IF NOT EXISTS ${tenant_schema}.customer_address (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    customer_id BIGINT       NOT NULL REFERENCES ${tenant_schema}.customer(id) ON DELETE CASCADE,
    kind        VARCHAR(12)  NOT NULL DEFAULT 'SHIPPING',   -- FISCAL, SHIPPING, BRANCH
    label       VARCHAR(80),                                -- alias ("Matriz", "Bodega norte")
    street      VARCHAR(200),
    ext_number  VARCHAR(30),
    int_number  VARCHAR(30),
    neighborhood VARCHAR(120),                              -- colonia
    city        VARCHAR(120),
    state       VARCHAR(120),
    zip_code    VARCHAR(10),
    country     VARCHAR(60)  NOT NULL DEFAULT 'México',
    reference   VARCHAR(200),
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_addr_kind CHECK (kind IN ('FISCAL','SHIPPING','BRANCH'))
);

CREATE INDEX IF NOT EXISTS idx_customer_address_cust ON ${tenant_schema}.customer_address (customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_salesperson  ON ${tenant_schema}.customer (salesperson);
CREATE INDEX IF NOT EXISTS idx_customer_name_lower    ON ${tenant_schema}.customer (lower(name));

-- Row-Level Security en la nueva tabla.
ALTER TABLE ${tenant_schema}.customer_address ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.customer_address FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_customer_address ON ${tenant_schema}.customer_address
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
