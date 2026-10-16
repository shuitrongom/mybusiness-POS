-- =====================================================================
-- MyBusiness Silva — V34 (tenant): SUBSISTEMA DE PANADERÍA.
--
-- Número de versión: V34.
--   Calculado contra el estado real del worktree v3 (= master, que ya incluye
--   venta-especial V33 = V33__sale_type_and_line_unit.sql). Máximo tenant real
--   observado al listar el directorio de migraciones: V33. No existe V34.
--   => esta migración toma V34. (Reconfirmado listando el directorio tenant.)
--
-- Construye el subsistema de panadería SOBRE el motor de inventario existente
-- (V3/V21): las entradas, ventas y mermas afectan existencias a través del
-- InventoryPort; esta migración solo agrega las tablas propias de panadería,
-- las banderas de producto y la trazabilidad de la merma.
--
-- Idempotente: CREATE TABLE/INDEX IF NOT EXISTS, ADD COLUMN IF NOT EXISTS,
-- INSERT ... ON CONFLICT DO NOTHING, y RLS con DROP POLICY IF EXISTS (RNF3).
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

-- ---------------------------------------------------------------------
-- 1) Catálogo extensible de estatus de entrada de producción.
--    Patrón de inventory_concept: agregar un estatus es un INSERT, no código.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.bakery_entry_status (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    code       VARCHAR(40)  NOT NULL,          -- clave estable (PRODUCED, REPROCESS, DONATION…)
    label      VARCHAR(120) NOT NULL,          -- etiqueta visible
    sort_order INTEGER      NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_bakery_entry_status_code UNIQUE (code)
);

INSERT INTO ${tenant_schema}.bakery_entry_status (code, label, sort_order) VALUES
    ('PRODUCED',  'Producción', 1),
    ('REPROCESS', 'Reproceso',  2),
    ('DONATION',  'Donación',   3)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------
-- 2) Entrada de producción (cabecera + renglones) con folio y actor.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.bakery_production (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    folio           VARCHAR(40),
    branch_id       BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    status_code     VARCHAR(40)   NOT NULL DEFAULT 'PRODUCED'
                    REFERENCES ${tenant_schema}.bakery_entry_status(code),
    production_date DATE          NOT NULL DEFAULT (now() AT TIME ZONE 'America/Mexico_City')::date,
    total_qty       NUMERIC(14,3) NOT NULL DEFAULT 0,
    total_value     NUMERIC(14,2) NOT NULL DEFAULT 0,
    notes           VARCHAR(500),
    created_by      VARCHAR(255),           -- actor (hoy el cajero; mañana el panadero)
    created_role    VARCHAR(40),            -- rol operativo del actor, para el reporte del admin
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ${tenant_schema}.bakery_production_line (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    production_id BIGINT        NOT NULL REFERENCES ${tenant_schema}.bakery_production(id) ON DELETE CASCADE,
    product_id    BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    description   VARCHAR(300),
    quantity      NUMERIC(14,3) NOT NULL,
    unit          VARCHAR(40),
    entry_price   NUMERIC(14,2) NOT NULL DEFAULT 0,  -- precio de entrada (base para la merma)
    unit_cost     NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total    NUMERIC(14,2) NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------
-- 3) Banderas de producto para la regla dura (solo pan, opt-in).
--    Default inocuo (FALSE) => la regla nace apagada para todo lo existente.
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.product
    ADD COLUMN IF NOT EXISTS is_bakery     BOOLEAN NOT NULL DEFAULT FALSE; -- artículo de panadería
ALTER TABLE ${tenant_schema}.product
    ADD COLUMN IF NOT EXISTS enforce_stock BOOLEAN NOT NULL DEFAULT FALSE; -- no vender sin existencia

-- ---------------------------------------------------------------------
-- 4) Trazabilidad de merma vendible, ligada por (sale_id, product_id).
--    sale_line_id queda NULLABLE y RESERVADA (el flujo actual la deja NULL,
--    porque registerSale no expone ids de renglón).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.bakery_waste_sale (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    sale_id      BIGINT        NOT NULL REFERENCES ${tenant_schema}.sale(id) ON DELETE CASCADE,
    sale_line_id BIGINT        REFERENCES ${tenant_schema}.sale_line(id) ON DELETE SET NULL,
                               -- RESERVADA: queda NULL en el flujo actual.
    product_id   BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    quantity     NUMERIC(14,3) NOT NULL,
    entry_price  NUMERIC(14,2) NOT NULL,   -- precio de entrada original
    waste_price  NUMERIC(14,2) NOT NULL,   -- precio de merma vendido (editable)
    difference   NUMERIC(14,2) NOT NULL,   -- entry_price - waste_price (por unidad)
    cashier      VARCHAR(255),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_bakery_waste_sale_sale_product UNIQUE (sale_id, product_id)
);

-- ---------------------------------------------------------------------
-- 5) Índices (Decisión 2.6 del design.md).
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_bakery_prod_branch_date ON ${tenant_schema}.bakery_production (branch_id, production_date);
CREATE INDEX IF NOT EXISTS idx_bakery_prodline_prod    ON ${tenant_schema}.bakery_production_line (product_id);
CREATE INDEX IF NOT EXISTS idx_bakery_prodline_doc     ON ${tenant_schema}.bakery_production_line (production_id);
CREATE INDEX IF NOT EXISTS idx_bakery_waste_sale       ON ${tenant_schema}.bakery_waste_sale (sale_id);
CREATE INDEX IF NOT EXISTS idx_bakery_waste_prod_date  ON ${tenant_schema}.bakery_waste_sale (product_id, created_at);
CREATE INDEX IF NOT EXISTS idx_product_is_bakery       ON ${tenant_schema}.product (is_bakery) WHERE is_bakery = TRUE;

-- ---------------------------------------------------------------------
-- 6) Row-Level Security en las 4 tablas nuevas.
--    DROP POLICY IF EXISTS es OBLIGATORIO (RNF3) para que la re-aplicación
--    manual nunca falle con "policy already exists" (V21 no lo tiene).
-- ---------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['bakery_entry_status','bakery_production',
                             'bakery_production_line','bakery_waste_sale']
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', '${tenant_schema}', t);
        EXECUTE format('ALTER TABLE %I.%I FORCE  ROW LEVEL SECURITY', '${tenant_schema}', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation_%s ON %I.%I', t, '${tenant_schema}', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation_%s ON %I.%I '
            || 'USING (tenant_id = current_setting(''app.current_tenant'', true)) '
            || 'WITH CHECK (tenant_id = current_setting(''app.current_tenant'', true))',
            t, '${tenant_schema}', t);
    END LOOP;
END
$$;
