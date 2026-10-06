-- =====================================================================
-- MyBusiness Silva — V17 (tenant): PROMOCIONES.
--
-- Bloque 4 de la Suite de Ventas. Modela promociones flexibles que superan a
-- las de los POS del mercado:
--   • Tipos: PERCENT (% de descuento), AMOUNT (importe fijo por unidad),
--     SPECIAL_PRICE (precio especial), NXM (lleva N paga M, p.ej. 2x1, 3x2).
--   • Ámbito: PRODUCT (productos específicos), CATEGORY (una categoría),
--     ALL (toda la tienda).
--   • Vigencia por fechas, mínimo de compra, tope de usos y activación.
--
-- El motor de aplicación vive en la capa de aplicación; aquí solo el modelo.
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

CREATE TABLE ${tenant_schema}.promotion (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    name          VARCHAR(160)  NOT NULL,
    type          VARCHAR(16)   NOT NULL,   -- PERCENT, AMOUNT, SPECIAL_PRICE, NXM
    scope         VARCHAR(12)   NOT NULL DEFAULT 'PRODUCT',   -- PRODUCT, CATEGORY, ALL
    category_id   BIGINT        REFERENCES ${tenant_schema}.category(id),
    -- Valor según el tipo:
    --   PERCENT       -> porcentaje (0..100) en 'value'
    --   AMOUNT        -> importe a descontar por unidad en 'value'
    --   SPECIAL_PRICE -> precio especial en 'value'
    --   NXM           -> 'buy_qty' (N) y 'pay_qty' (M)
    value         NUMERIC(14,2) NOT NULL DEFAULT 0,
    buy_qty       NUMERIC(14,3) NOT NULL DEFAULT 0,
    pay_qty       NUMERIC(14,3) NOT NULL DEFAULT 0,
    min_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,   -- compra mínima del renglón/ticket para aplicar
    starts_on     DATE,
    ends_on       DATE,
    max_uses      INTEGER,                            -- tope de usos (null = ilimitado)
    used_count    INTEGER       NOT NULL DEFAULT 0,
    priority      INTEGER       NOT NULL DEFAULT 0,   -- mayor prioridad se evalúa primero
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_promo_type  CHECK (type IN ('PERCENT','AMOUNT','SPECIAL_PRICE','NXM')),
    CONSTRAINT chk_promo_scope CHECK (scope IN ('PRODUCT','CATEGORY','ALL'))
);

-- Productos incluidos en una promoción de ámbito PRODUCT.
CREATE TABLE ${tenant_schema}.promotion_product (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63) NOT NULL DEFAULT current_setting('app.current_tenant', true),
    promotion_id BIGINT      NOT NULL REFERENCES ${tenant_schema}.promotion(id) ON DELETE CASCADE,
    product_id   BIGINT      NOT NULL REFERENCES ${tenant_schema}.product(id) ON DELETE CASCADE,
    CONSTRAINT uq_promo_product UNIQUE (promotion_id, product_id)
);

CREATE INDEX idx_promotion_active   ON ${tenant_schema}.promotion (active) WHERE active = TRUE;
CREATE INDEX idx_promoproduct_promo ON ${tenant_schema}.promotion_product (promotion_id);
CREATE INDEX idx_promoproduct_prod  ON ${tenant_schema}.promotion_product (product_id);

-- Row-Level Security.
ALTER TABLE ${tenant_schema}.promotion         ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.promotion_product ENABLE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.promotion         FORCE ROW LEVEL SECURITY;
ALTER TABLE ${tenant_schema}.promotion_product FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_promotion ON ${tenant_schema}.promotion
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
CREATE POLICY tenant_isolation_promotion_product ON ${tenant_schema}.promotion_product
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
