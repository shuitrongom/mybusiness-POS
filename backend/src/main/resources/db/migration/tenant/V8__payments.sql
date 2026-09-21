-- =====================================================================
-- Recargas de tiempo aire y pago de servicios (por tenant).
--   payment_operation: registra cada operación (recarga o servicio), su resultado,
--                      la comisión ganada y la referencia del agregador para conciliación.
-- Con RLS por tenant.
-- =====================================================================

CREATE TABLE ${tenant_schema}.payment_operation (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    op_type        VARCHAR(15)   NOT NULL,   -- RECHARGE (tiempo aire), SERVICE (pago de servicio)
    carrier        VARCHAR(60),              -- compañía (Telcel, Movistar, CFE, etc.)
    reference      VARCHAR(120)  NOT NULL,   -- teléfono a recargar o número de recibo
    amount         NUMERIC(14,2) NOT NULL,   -- monto cobrado al cliente
    commission     NUMERIC(14,2) NOT NULL DEFAULT 0,  -- comisión ganada por el negocio
    status         VARCHAR(12)   NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED
    provider_folio VARCHAR(120),             -- folio devuelto por el agregador
    error          VARCHAR(400),
    branch_id      BIGINT        REFERENCES ${tenant_schema}.branch(id),
    cashier        VARCHAR(255),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_op_type CHECK (op_type IN ('RECHARGE','SERVICE')),
    CONSTRAINT chk_op_status CHECK (status IN ('PENDING','SUCCESS','FAILED'))
);

CREATE INDEX idx_payment_op_type   ON ${tenant_schema}.payment_operation (op_type);
CREATE INDEX idx_payment_op_status ON ${tenant_schema}.payment_operation (status);
CREATE INDEX idx_payment_op_created ON ${tenant_schema}.payment_operation (created_at);

ALTER TABLE ${tenant_schema}.payment_operation ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.payment_operation FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_payment_operation ON ${tenant_schema}.payment_operation
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
