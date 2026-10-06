-- =====================================================================
-- MyBusiness Silva — V12 (tenant): catálogo de producto de nivel ENTERPRISE.
--
-- Amplía product con la información profesional que manejan los POS líderes
-- del mercado: descripción, marca, proveedor principal, impuesto, listas de
-- precio múltiples (mayoreo/medio mayoreo/especial), costos adicionales,
-- niveles de inventario (mínimo/máximo/punto de reorden) y banderas de
-- comportamiento (control de inventario, lotes, venta bajo costo, etc.).
--
-- Todo con ADD COLUMN IF NOT EXISTS para ser idempotente. Los defaults se
-- eligen para no cambiar el comportamiento actual de los productos ya creados.
--
-- Flyway sustituye ${tenant_schema} por el schema destino.
-- =====================================================================

-- ---- Identidad y clasificación ----
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS description   TEXT;
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS brand         VARCHAR(160);
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS supplier_id   BIGINT REFERENCES ${tenant_schema}.supplier(id);

-- ---- Impuesto (para desglose fiscal). Tasa en fracción: 0.16 = IVA 16% ----
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS tax_rate      NUMERIC(5,4) NOT NULL DEFAULT 0.16;
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS ieps_rate     NUMERIC(5,4) NOT NULL DEFAULT 0;

-- ---- Listas de precio adicionales (precio 1 = 'price' ya existente) ----
-- price2 = mayoreo, price3 = medio mayoreo, price4 = especial, price5 = distribuidor.
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS price2        NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS price3        NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS price4        NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS price5        NUMERIC(12,2) NOT NULL DEFAULT 0;

-- ---- Costos adicionales ----
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS last_cost     NUMERIC(12,2) NOT NULL DEFAULT 0;  -- costo de la última compra
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS avg_cost      NUMERIC(12,2) NOT NULL DEFAULT 0;  -- costo promedio

-- ---- Niveles de inventario a nivel producto (guía de reabasto) ----
-- Min = punto de reorden (cuándo comprar); Max = nivel objetivo (hasta cuánto).
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS min_stock     NUMERIC(14,3) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS max_stock     NUMERIC(14,3) NOT NULL DEFAULT 0;
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS reorder_point NUMERIC(14,3) NOT NULL DEFAULT 0;

-- ---- Banderas de comportamiento ----
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS for_sale          BOOLEAN NOT NULL DEFAULT TRUE;   -- se puede vender
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS track_inventory   BOOLEAN NOT NULL DEFAULT TRUE;   -- controla existencias
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS track_lots        BOOLEAN NOT NULL DEFAULT FALSE;  -- controla lotes/caducidad
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS allow_below_cost  BOOLEAN NOT NULL DEFAULT FALSE;  -- permite vender bajo costo
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS blocked           BOOLEAN NOT NULL DEFAULT FALSE;  -- bloqueado (no operable)
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS is_composite      BOOLEAN NOT NULL DEFAULT FALSE;  -- artículo compuesto (kit)
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS on_sale           BOOLEAN NOT NULL DEFAULT FALSE;  -- en oferta

-- ---- Lealtad ----
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS loyalty_points    NUMERIC(10,2) NOT NULL DEFAULT 0; -- puntos que otorga

CREATE INDEX IF NOT EXISTS idx_product_supplier ON ${tenant_schema}.product (supplier_id);
CREATE INDEX IF NOT EXISTS idx_product_active   ON ${tenant_schema}.product (active) WHERE active = TRUE;
