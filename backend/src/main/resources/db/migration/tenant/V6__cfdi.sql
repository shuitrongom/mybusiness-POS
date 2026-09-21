-- =====================================================================
-- Facturación electrónica CFDI 4.0 (por tenant).
--   - cfdi: comprobantes emitidos (XML/PDF, UUID, estado).
--   - cfdi_payment_complement: complementos de pago de facturas a crédito.
-- Con RLS por tenant.
-- =====================================================================

CREATE TABLE ${tenant_schema}.cfdi (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    sale_id        BIGINT,                              -- venta origen (nulo para factura global)
    kind           VARCHAR(20)   NOT NULL DEFAULT 'INVOICE',  -- INVOICE, GLOBAL, PAYMENT
    -- Datos fiscales del receptor (CFDI 4.0 requiere nombre exacto, RFC, CP y régimen).
    receiver_rfc   VARCHAR(13)   NOT NULL,
    receiver_name  VARCHAR(300)  NOT NULL,
    receiver_zip   VARCHAR(5)    NOT NULL,
    receiver_regime VARCHAR(5)   NOT NULL,
    cfdi_use       VARCHAR(5)    NOT NULL,              -- uso del CFDI (G01, G03, S01...)
    subtotal       NUMERIC(14,2) NOT NULL,
    tax            NUMERIC(14,2) NOT NULL DEFAULT 0,
    total          NUMERIC(14,2) NOT NULL,
    -- Resultado del timbrado.
    status         VARCHAR(15)   NOT NULL DEFAULT 'PENDING', -- PENDING, STAMPED, CANCELED, ERROR
    uuid           VARCHAR(40),                          -- folio fiscal (UUID) tras timbrar
    xml            TEXT,                                 -- XML timbrado
    pdf_path       VARCHAR(400),                         -- ruta del PDF almacenado
    stamp_error    VARCHAR(600),
    idempotency_key VARCHAR(80)  UNIQUE,                 -- evita timbrar dos veces la misma solicitud
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    stamped_at     TIMESTAMPTZ,
    canceled_at    TIMESTAMPTZ,
    CONSTRAINT chk_cfdi_kind CHECK (kind IN ('INVOICE','GLOBAL','PAYMENT')),
    CONSTRAINT chk_cfdi_status CHECK (status IN ('PENDING','STAMPED','CANCELED','ERROR'))
);

-- Complemento de pagos: registra los pagos aplicados a facturas a crédito.
CREATE TABLE ${tenant_schema}.cfdi_payment_complement (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    cfdi_id        BIGINT        NOT NULL REFERENCES ${tenant_schema}.cfdi(id),
    paid_amount    NUMERIC(14,2) NOT NULL,
    payment_form   VARCHAR(5)    NOT NULL,               -- forma de pago SAT (01 efectivo, 03 transferencia...)
    payment_date   DATE          NOT NULL,
    uuid           VARCHAR(40),
    status         VARCHAR(15)   NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_pc_status CHECK (status IN ('PENDING','STAMPED','CANCELED','ERROR'))
);

CREATE INDEX idx_cfdi_sale   ON ${tenant_schema}.cfdi (sale_id);
CREATE INDEX idx_cfdi_status ON ${tenant_schema}.cfdi (status);
CREATE INDEX idx_cfdi_uuid   ON ${tenant_schema}.cfdi (uuid);

ALTER TABLE ${tenant_schema}.cfdi                    ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.cfdi_payment_complement ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.cfdi                    FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.cfdi_payment_complement FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_cfdi ON ${tenant_schema}.cfdi
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_cfdi_pc ON ${tenant_schema}.cfdi_payment_complement
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
