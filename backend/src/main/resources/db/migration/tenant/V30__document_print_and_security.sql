-- =====================================================================
-- MyBusiness Silva — V30 (tenant): bitácora de comprobantes impresos y
-- refuerzo de seguridad del flujo de caja.
--
-- document_print: registro (evidencia) de todo comprobante generado/impreso
-- que respalda un movimiento de dinero o mercancía: corte de caja (X/Z),
-- movimiento de efectivo, devolución, traspaso, venta. Permite al dueño
-- consultarlos clasificados y detectar/auditar cualquier movimiento.
--
-- Fija app.current_tenant para la RLS. Idempotente. Flyway sustituye ${tenant_schema}.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

CREATE TABLE IF NOT EXISTS ${tenant_schema}.document_print (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    doc_type     VARCHAR(30)   NOT NULL,     -- SALE, CUT_X, CUT_Z, CASH_MOVEMENT, RETURN, TRANSFER, VOID
    folio        VARCHAR(60),                -- folio del documento (V-123, Z-4, MOV-...)
    title        VARCHAR(120),               -- título legible del comprobante
    shift_id     BIGINT,                     -- turno relacionado (si aplica)
    branch_id    BIGINT,
    amount       NUMERIC(14,2),              -- importe principal (para reportes)
    actor        VARCHAR(255),               -- quién lo generó
    reprint      BOOLEAN       NOT NULL DEFAULT FALSE,   -- true si fue una reimpresión
    payload      JSONB,                      -- datos del comprobante (para reimprimir tal cual)
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_doc_type CHECK (doc_type IN
        ('SALE','CUT_X','CUT_Z','CASH_MOVEMENT','RETURN','TRANSFER','VOID'))
);

CREATE INDEX IF NOT EXISTS idx_docprint_type    ON ${tenant_schema}.document_print (doc_type);
CREATE INDEX IF NOT EXISTS idx_docprint_actor   ON ${tenant_schema}.document_print (actor);
CREATE INDEX IF NOT EXISTS idx_docprint_created ON ${tenant_schema}.document_print (created_at);
CREATE INDEX IF NOT EXISTS idx_docprint_shift   ON ${tenant_schema}.document_print (shift_id);

ALTER TABLE ${tenant_schema}.document_print ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.document_print FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_document_print ON ${tenant_schema}.document_print
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- Índice para la consulta de "¿ya cerró hoy?" (bloqueo de reapertura por día).
CREATE INDEX IF NOT EXISTS idx_shift_closed_today
    ON ${tenant_schema}.shift (opened_by, cash_register_id, status, business_date);
