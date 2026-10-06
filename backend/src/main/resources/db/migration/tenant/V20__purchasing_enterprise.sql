-- =====================================================================
-- MyBusiness Silva — V20 (tenant): SUITE DE COMPRAS nivel ENTERPRISE.
--
-- Módulo 2. Eleva compras y proveedores al nivel que exige un POS mexicano
-- profesional, con impuestos REALES de México:
--   • IVA 16% (general), IVA 8% (región fronteriza), IVA 0% (alimentos/medicinas),
--     EXENTO, IEPS (variable), y RETENCIONES (IVA retenido, ISR retenido —
--     comunes en fletes/servicios/honorarios). También "donativo".
--   • Órdenes de compra (ORDERED) que se convierten en recepción (RECEIVED).
--   • Devoluciones de compra ligadas o independientes.
--   • Proveedores enterprise (crédito, descuentos por volumen, clasificación,
--     contactos, rol/periodicidad de visita y agenda de visitas).
--   • Compras en moneda extranjera con tipo de cambio.
--
-- Idempotente donde amplía (IF NOT EXISTS). Flyway sustituye ${tenant_schema}.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Proveedor enterprise (amplía supplier de V7)
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS external_code   VARCHAR(60);   -- clave del proveedor
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS country         VARCHAR(60)  NOT NULL DEFAULT 'México';
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS zip_code        VARCHAR(10);
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS street          VARCHAR(200);
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS neighborhood    VARCHAR(120);  -- colonia
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS town            VARCHAR(120);  -- población
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS city            VARCHAR(120);
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS state           VARCHAR(120);
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS credit_days     INTEGER      NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS credit_limit    NUMERIC(14,2) NOT NULL DEFAULT 0;
-- Hasta 5 descuentos por volumen (como en MyBusiness).
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS discount1       NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS discount2       NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS discount3       NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS discount4       NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS discount5       NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS classification  VARCHAR(40)  DEFAULT 'GENERAL';
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS review_payment  VARCHAR(120);  -- días de revisión y pago
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS affects_inventory_only BOOLEAN NOT NULL DEFAULT FALSE; -- afectar compras sin inventario (FALSE = sí afecta inv)
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS skip_payable    BOOLEAN NOT NULL DEFAULT FALSE; -- afectar compras sin afectar CxP
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS notes           TEXT;
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS image_url       TEXT;
-- Rol de visita del proveedor.
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS visit_periodicity VARCHAR(12) DEFAULT 'NONE'; -- WEEKLY, BIWEEKLY, MONTHLY, OTHER, NONE
ALTER TABLE ${tenant_schema}.supplier ADD COLUMN IF NOT EXISTS visit_days      VARCHAR(40);  -- ej. "L,M,V" para semanal

-- ---------------------------------------------------------------------
-- 2) Contactos del proveedor
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.supplier_contact (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    supplier_id BIGINT       NOT NULL REFERENCES ${tenant_schema}.supplier(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    role        VARCHAR(120),
    phone       VARCHAR(40),
    email       VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 3) Compra enterprise (amplía purchase de V7)
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS folio          VARCHAR(40);
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS doc_type       VARCHAR(12)  NOT NULL DEFAULT 'PURCHASE'; -- ORDER (orden de compra), PURCHASE (recepción)
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS currency       VARCHAR(3)   NOT NULL DEFAULT 'MXN';
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS exchange_rate  NUMERIC(12,4) NOT NULL DEFAULT 1;
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS subtotal       NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS discount       NUMERIC(14,2) NOT NULL DEFAULT 0;  -- descuento global
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS tax            NUMERIC(14,2) NOT NULL DEFAULT 0;  -- IVA + IEPS trasladados
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS retention      NUMERIC(14,2) NOT NULL DEFAULT 0;  -- IVA/ISR retenidos
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS donation       NUMERIC(14,2) NOT NULL DEFAULT 0;  -- donativo
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS discount_pct   NUMERIC(5,2)  NOT NULL DEFAULT 0;  -- % descuento general por partida
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS cfdi_uuid      VARCHAR(40);   -- folio fiscal (GUID) del CFDI del proveedor
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS notes          TEXT;
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS created_by     VARCHAR(255);
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS received_at    TIMESTAMPTZ;
ALTER TABLE ${tenant_schema}.purchase ADD COLUMN IF NOT EXISTS expected_date  DATE;          -- fecha de entrega esperada (órdenes)

-- Ampliar el CHECK de status para permitir ORDERED, RECEIVED y CANCELLED.
ALTER TABLE ${tenant_schema}.purchase DROP CONSTRAINT IF EXISTS chk_purchase_status;
ALTER TABLE ${tenant_schema}.purchase ADD CONSTRAINT chk_purchase_status
    CHECK (status IN ('ORDERED','RECEIVED','CANCELLED'));

-- ---------------------------------------------------------------------
-- 4) Renglón de compra enterprise (amplía purchase_line de V7)
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.purchase_line ADD COLUMN IF NOT EXISTS description   VARCHAR(300);
ALTER TABLE ${tenant_schema}.purchase_line ADD COLUMN IF NOT EXISTS discount      NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.purchase_line ADD COLUMN IF NOT EXISTS discount_extra NUMERIC(14,2) NOT NULL DEFAULT 0; -- descuento adicional
ALTER TABLE ${tenant_schema}.purchase_line ADD COLUMN IF NOT EXISTS tax_rate      NUMERIC(5,4)  NOT NULL DEFAULT 0.16;
ALTER TABLE ${tenant_schema}.purchase_line ADD COLUMN IF NOT EXISTS tax_amount    NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.purchase_line ADD COLUMN IF NOT EXISTS expected_date DATE;   -- fecha de entrega del renglón

-- ---------------------------------------------------------------------
-- 5) Cuentas por pagar enterprise (amplía account_payable de V7)
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.account_payable ADD COLUMN IF NOT EXISTS branch_id   BIGINT;
ALTER TABLE ${tenant_schema}.account_payable ADD COLUMN IF NOT EXISTS currency    VARCHAR(3) NOT NULL DEFAULT 'MXN';
ALTER TABLE ${tenant_schema}.account_payable ADD COLUMN IF NOT EXISTS invoice_ref VARCHAR(120);

-- ---------------------------------------------------------------------
-- 6) Devoluciones de compra
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.purchase_return (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    folio        VARCHAR(40),
    purchase_id  BIGINT        REFERENCES ${tenant_schema}.purchase(id),  -- compra origen (opcional)
    supplier_id  BIGINT        REFERENCES ${tenant_schema}.supplier(id),
    branch_id    BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    reason       VARCHAR(300),
    total        NUMERIC(14,2) NOT NULL DEFAULT 0,
    processed_by VARCHAR(255),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ${tenant_schema}.purchase_return_line (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    return_id   BIGINT        NOT NULL REFERENCES ${tenant_schema}.purchase_return(id) ON DELETE CASCADE,
    product_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    description VARCHAR(300)  NOT NULL,
    quantity    NUMERIC(14,3) NOT NULL,
    unit_cost   NUMERIC(14,2) NOT NULL,
    line_total  NUMERIC(14,2) NOT NULL
);

-- ---------------------------------------------------------------------
-- 7) Agenda de visitas de proveedor
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.supplier_visit (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    supplier_id    BIGINT        NOT NULL REFERENCES ${tenant_schema}.supplier(id) ON DELETE CASCADE,
    visit_date     DATE          NOT NULL,
    visit_time     VARCHAR(10),
    estimated_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    purchase_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,
    visited        BOOLEAN       NOT NULL DEFAULT FALSE,
    notes          VARCHAR(300),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_purchase_doctype   ON ${tenant_schema}.purchase (doc_type);
CREATE INDEX IF NOT EXISTS idx_purchase_status    ON ${tenant_schema}.purchase (status);
CREATE INDEX IF NOT EXISTS idx_payable_supplier   ON ${tenant_schema}.account_payable (supplier_id);
CREATE INDEX IF NOT EXISTS idx_preturn_supplier   ON ${tenant_schema}.purchase_return (supplier_id);
CREATE INDEX IF NOT EXISTS idx_svisit_date        ON ${tenant_schema}.supplier_visit (visit_date);
CREATE INDEX IF NOT EXISTS idx_scontact_supplier  ON ${tenant_schema}.supplier_contact (supplier_id);

-- ---------------------------------------------------------------------
-- Row-Level Security en las tablas nuevas.
-- ---------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['supplier_contact','purchase_return','purchase_return_line','supplier_visit']
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
