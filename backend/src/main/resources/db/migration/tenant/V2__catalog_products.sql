-- =====================================================================
-- Catálogo de productos del negocio (por tenant).
-- Cada negocio mantiene su propio catálogo aislado. Los campos específicos
-- del giro se guardan en 'attributes' (JSONB), definidos por el perfil de giro.
-- Se aplica RLS como en el resto de tablas de tenant.
-- =====================================================================

-- Categorías de producto del negocio.
CREATE TABLE ${tenant_schema}.category (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    name       VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_category_name UNIQUE (name)
);

-- Productos del negocio.
CREATE TABLE ${tenant_schema}.product (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    sku            VARCHAR(64),
    name           VARCHAR(300)  NOT NULL,
    category_id    BIGINT        REFERENCES ${tenant_schema}.category(id),
    unit           VARCHAR(40)   NOT NULL DEFAULT 'pieza',
    sold_by_weight BOOLEAN       NOT NULL DEFAULT FALSE,
    sat_prod_serv  VARCHAR(8),
    sat_unit       VARCHAR(3),
    price          NUMERIC(12,2) NOT NULL DEFAULT 0,
    cost           NUMERIC(12,2) NOT NULL DEFAULT 0,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    -- Campos específicos del giro (caducidad, lote, calibre, etc.).
    attributes     JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Códigos de barras del producto (un producto puede tener varios).
CREATE TABLE ${tenant_schema}.product_barcode (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63) NOT NULL DEFAULT current_setting('app.current_tenant', true),
    product_id BIGINT      NOT NULL REFERENCES ${tenant_schema}.product(id) ON DELETE CASCADE,
    barcode    VARCHAR(64) NOT NULL,
    CONSTRAINT uq_product_barcode UNIQUE (barcode)
);

CREATE INDEX idx_product_name ON ${tenant_schema}.product (lower(name));
CREATE INDEX idx_product_sku  ON ${tenant_schema}.product (sku);

-- Row-Level Security en las tablas del catálogo del tenant.
ALTER TABLE ${tenant_schema}.category        ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.product         ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.product_barcode ENABLE ROW LEVEL SECURITY;

ALTER TABLE ${tenant_schema}.category        FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.product         FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.product_barcode FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_category ON ${tenant_schema}.category
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY tenant_isolation_product ON ${tenant_schema}.product
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY tenant_isolation_product_barcode ON ${tenant_schema}.product_barcode
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
