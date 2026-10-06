-- =====================================================================
-- MyBusiness Silva — V19 (tenant): CORTES de caja X y Z persistidos.
--
-- Bloque 6 de la Suite de Ventas. El cierre de turno ya calculaba el corte al
-- vuelo; aquí se persiste como DOCUMENTO consultable e imprimible:
--   • Corte X — lectura parcial durante el turno (no cierra la caja). Informativo.
--   • Corte Z — corte de cierre del turno (fin de jornada). Cierra el turno.
--
-- Guarda el desglose por método de pago, entradas/salidas de efectivo, el
-- efectivo esperado vs contado y la diferencia.
--
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

CREATE TABLE ${tenant_schema}.cash_cut (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    folio          VARCHAR(40),
    shift_id       BIGINT        NOT NULL REFERENCES ${tenant_schema}.shift(id),
    cut_type       VARCHAR(1)    NOT NULL,   -- 'X' (parcial) o 'Z' (cierre)
    opening_float  NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_sales     NUMERIC(14,2) NOT NULL DEFAULT 0,
    card_sales     NUMERIC(14,2) NOT NULL DEFAULT 0,
    transfer_sales NUMERIC(14,2) NOT NULL DEFAULT 0,
    voucher_sales  NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_in        NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_out       NUMERIC(14,2) NOT NULL DEFAULT 0,
    expected_cash  NUMERIC(14,2) NOT NULL DEFAULT 0,
    counted_cash   NUMERIC(14,2),
    difference     NUMERIC(14,2),
    sales_count    INTEGER       NOT NULL DEFAULT 0,
    total_sales    NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_by     VARCHAR(255),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_cut_type CHECK (cut_type IN ('X','Z'))
);

CREATE INDEX idx_cashcut_shift   ON ${tenant_schema}.cash_cut (shift_id);
CREATE INDEX idx_cashcut_created ON ${tenant_schema}.cash_cut (created_at);

ALTER TABLE ${tenant_schema}.cash_cut ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.cash_cut FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_cash_cut ON ${tenant_schema}.cash_cut
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
