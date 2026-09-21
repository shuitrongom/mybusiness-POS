-- =====================================================================
-- Compras/Proveedores y Clientes/CRM (por tenant).
--   Compras:   supplier, purchase, purchase_line, account_payable.
--   Clientes:  customer, account_receivable, loyalty_account, loyalty_movement.
-- Con RLS por tenant.
-- =====================================================================

-- ---- Proveedores ----
CREATE TABLE ${tenant_schema}.supplier (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    name       VARCHAR(300) NOT NULL,
    rfc        VARCHAR(13),
    phone      VARCHAR(40),
    email      VARCHAR(255),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---- Compras ----
CREATE TABLE ${tenant_schema}.purchase (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    supplier_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.supplier(id),
    branch_id    BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    invoice_ref  VARCHAR(120),
    total        NUMERIC(14,2) NOT NULL DEFAULT 0,
    on_credit    BOOLEAN       NOT NULL DEFAULT FALSE,
    status       VARCHAR(12)   NOT NULL DEFAULT 'RECEIVED',  -- ORDERED, RECEIVED
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_purchase_status CHECK (status IN ('ORDERED','RECEIVED'))
);

CREATE TABLE ${tenant_schema}.purchase_line (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    purchase_id BIGINT        NOT NULL REFERENCES ${tenant_schema}.purchase(id) ON DELETE CASCADE,
    product_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    quantity    NUMERIC(14,3) NOT NULL,
    unit_cost   NUMERIC(14,2) NOT NULL,
    line_total  NUMERIC(14,2) NOT NULL
);

-- ---- Cuentas por pagar (a proveedores) ----
CREATE TABLE ${tenant_schema}.account_payable (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    supplier_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.supplier(id),
    purchase_id  BIGINT        REFERENCES ${tenant_schema}.purchase(id),
    amount       NUMERIC(14,2) NOT NULL,
    paid         NUMERIC(14,2) NOT NULL DEFAULT 0,
    status       VARCHAR(10)   NOT NULL DEFAULT 'OPEN',  -- OPEN, PAID
    due_date     DATE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_payable_status CHECK (status IN ('OPEN','PAID'))
);

-- ---- Clientes ----
CREATE TABLE ${tenant_schema}.customer (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    name          VARCHAR(300)  NOT NULL,
    rfc           VARCHAR(13),
    phone         VARCHAR(40),
    email         VARCHAR(255),
    credit_limit  NUMERIC(14,2) NOT NULL DEFAULT 0,
    credit_used   NUMERIC(14,2) NOT NULL DEFAULT 0,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---- Cuentas por cobrar (a clientes) ----
CREATE TABLE ${tenant_schema}.account_receivable (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    customer_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.customer(id),
    sale_id      BIGINT,
    amount       NUMERIC(14,2) NOT NULL,
    paid         NUMERIC(14,2) NOT NULL DEFAULT 0,
    status       VARCHAR(10)   NOT NULL DEFAULT 'OPEN',  -- OPEN, PAID
    due_date     DATE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_receivable_status CHECK (status IN ('OPEN','PAID'))
);

-- ---- Lealtad / monedero electrónico ----
CREATE TABLE ${tenant_schema}.loyalty_account (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    customer_id BIGINT        NOT NULL UNIQUE REFERENCES ${tenant_schema}.customer(id),
    balance     NUMERIC(14,2) NOT NULL DEFAULT 0,   -- saldo del monedero / puntos
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE ${tenant_schema}.loyalty_movement (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    customer_id BIGINT        NOT NULL REFERENCES ${tenant_schema}.customer(id),
    direction   VARCHAR(10)   NOT NULL,  -- EARN (acumula), REDEEM (canjea)
    amount      NUMERIC(14,2) NOT NULL,
    balance_after NUMERIC(14,2) NOT NULL,
    reference   VARCHAR(120),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_loyalty_direction CHECK (direction IN ('EARN','REDEEM'))
);

CREATE INDEX idx_purchase_supplier ON ${tenant_schema}.purchase (supplier_id);
CREATE INDEX idx_payable_status    ON ${tenant_schema}.account_payable (status);
CREATE INDEX idx_receivable_cust   ON ${tenant_schema}.account_receivable (customer_id);
CREATE INDEX idx_receivable_status ON ${tenant_schema}.account_receivable (status);

-- Row-Level Security en todas las tablas nuevas.
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['supplier','purchase','purchase_line','account_payable',
                             'customer','account_receivable','loyalty_account','loyalty_movement']
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
