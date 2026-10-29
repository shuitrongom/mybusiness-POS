-- =====================================================================
-- MyBusiness Silva — V47 (tenant): MÓDULO PANADERO (materia prima + tanda).
--
-- Número de versión: V47.
--   La más alta tenant es V46__shift_close_mode.sql ("Current version of schema
--   tenant_9: 46"). No existe V47. => esta migración toma V47.
--
-- Construye el inventario AISLADO de materia prima (kardex propio) y la TANDA
-- (hornada) que liga el consumo de insumos con la producción de pan. NO toca
-- product/inventory_* ni ninguna migración desplegada (V42–V46). La pata pan de
-- la tanda reutiliza el flujo de producción existente (registerProduction →
-- inventory_stock); esta migración NO crea inventario de pan nuevo.
--
-- Aditiva e idempotente: CREATE TABLE/INDEX IF NOT EXISTS, INSERT ... ON CONFLICT
-- DO NOTHING, y RLS con DROP POLICY IF EXISTS (patrón V34). Flyway sustituye
-- ${tenant_schema}.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

-- ---------------------------------------------------------------------
-- 1) Catálogo de materia prima (insumos). Aislado por construcción: estas tablas
--    referencian raw_material(id), nunca product(id), así que un insumo jamás
--    puede aparecer en POS/ventas/pan.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.raw_material (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    sku          VARCHAR(60),                        -- opcional
    name         VARCHAR(160)  NOT NULL,
    unit         VARCHAR(40)   NOT NULL,             -- 'kg' | 'litro' | 'pieza' (validado en back)
    unit_cost    NUMERIC(14,4) NOT NULL DEFAULT 0,   -- costo por unidad (4 decimales: insumos a granel)
    min_quantity NUMERIC(14,3) NOT NULL DEFAULT 0,   -- umbral de alerta (informativo)
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Índices únicos PARCIALES (no bloquear valores de filas que ya no cuentan):
--   nombre único case-insensitive SOLO entre activos (permite reutilizar el nombre
--   de un insumo retirado; impide dos insumos ACTIVOS con el mismo nombre):
CREATE UNIQUE INDEX IF NOT EXISTS uq_raw_material_name
    ON ${tenant_schema}.raw_material (lower(name)) WHERE active = TRUE;
--   sku único solo cuando no es null:
CREATE UNIQUE INDEX IF NOT EXISTS uq_raw_material_sku
    ON ${tenant_schema}.raw_material (sku) WHERE sku IS NOT NULL;

-- ---------------------------------------------------------------------
-- 2) Existencia de materia prima por sucursal.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.raw_material_stock (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    raw_material_id BIGINT        NOT NULL REFERENCES ${tenant_schema}.raw_material(id),
    branch_id       BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    quantity        NUMERIC(14,3) NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_rm_stock UNIQUE (raw_material_id, branch_id)
);

-- ---------------------------------------------------------------------
-- 3) Kardex propio de insumos. unit_cost se CONGELA en cada movimiento para que el
--    costo histórico de MP consumida no cambie si luego se re-cotiza el insumo.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.raw_material_movement (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    raw_material_id BIGINT        NOT NULL REFERENCES ${tenant_schema}.raw_material(id),
    branch_id       BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    movement_type   VARCHAR(20)   NOT NULL,            -- 'CONSUME' | 'PURCHASE_RM' | 'ADJUSTMENT_RM'
    quantity        NUMERIC(14,3) NOT NULL,            -- negativo sale (CONSUME), positivo entra
    balance_after   NUMERIC(14,3) NOT NULL,
    unit_cost       NUMERIC(14,4) NOT NULL DEFAULT 0,  -- costo congelado al movimiento
    reason          VARCHAR(200),
    reference       VARCHAR(80),                       -- 'BATCH:'||batch_id para consumo de tanda
    actor           VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_rm_movement_type CHECK (movement_type IN ('CONSUME','PURCHASE_RM','ADJUSTMENT_RM'))
);

-- ---------------------------------------------------------------------
-- 4) Cabecera de la TANDA (hornada). Documento que liga MP consumida con pan
--    producido. production_id es NULLABLE solo durante el tx (insert-then-update):
--    al confirmar, toda tanda tiene production_id poblado (el servicio lo rellena
--    en el mismo tx). value_over_inputs NO es margen neto (no incluye mano de obra,
--    energía ni unit_cost del pan): es "Valor del pan − Costo de insumos".
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.bakery_batch (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    folio             VARCHAR(40),                      -- 'T-'||id
    branch_id         BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    batch_date        DATE          NOT NULL,           -- fecha MX explícita del servicio
    production_id     BIGINT        REFERENCES ${tenant_schema}.bakery_production(id),  -- pata pan
    input_cost        NUMERIC(14,2) NOT NULL DEFAULT 0, -- Σ costo MP consumida (congelado)
    output_value      NUMERIC(14,2) NOT NULL DEFAULT 0, -- Σ valor pan producido (entry_price·qty)
    value_over_inputs NUMERIC(14,2) NOT NULL DEFAULT 0, -- output_value - input_cost (NO margen neto)
    notes             VARCHAR(500),
    created_by        VARCHAR(255),                     -- actor: actor.subject() (el panadero)
    created_role      VARCHAR(40),                      -- 'PANADERO' (o OWNER/ADMIN/…)
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Invariante materializada: ata el valor derivado a sus sumandos (barrera dura).
    CONSTRAINT chk_value_over_inputs CHECK (value_over_inputs = output_value - input_cost)
);

-- ---------------------------------------------------------------------
-- 5) Líneas de insumo consumido (congeladas para el histórico de costo).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.bakery_batch_input (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    batch_id        BIGINT        NOT NULL REFERENCES ${tenant_schema}.bakery_batch(id) ON DELETE CASCADE,
    raw_material_id BIGINT        NOT NULL REFERENCES ${tenant_schema}.raw_material(id),
    quantity        NUMERIC(14,3) NOT NULL,             -- cantidad consumida (positiva)
    unit            VARCHAR(40),                         -- copiado de raw_material.unit (histórico)
    unit_cost       NUMERIC(14,4) NOT NULL DEFAULT 0,    -- congelado
    line_cost       NUMERIC(14,2) NOT NULL DEFAULT 0     -- quantity·unit_cost
);

-- ---------------------------------------------------------------------
-- 6) Líneas de pan producido (espejo para rendimiento, ligado a la tanda).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.bakery_batch_output (
    id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    batch_id   BIGINT        NOT NULL REFERENCES ${tenant_schema}.bakery_batch(id) ON DELETE CASCADE,
    product_id BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    quantity   NUMERIC(14,3) NOT NULL,                  -- piezas producidas
    unit       VARCHAR(40),
    entry_price NUMERIC(14,2) NOT NULL DEFAULT 0,        -- precio de la ficha (congelado)
    line_value  NUMERIC(14,2) NOT NULL DEFAULT 0         -- quantity·entry_price
);

-- ---------------------------------------------------------------------
-- 7) Índices.
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rm_stock_branch      ON ${tenant_schema}.raw_material_stock (branch_id);
CREATE INDEX IF NOT EXISTS idx_rm_mov_rm_date       ON ${tenant_schema}.raw_material_movement (raw_material_id, created_at);
CREATE INDEX IF NOT EXISTS idx_rm_mov_reference     ON ${tenant_schema}.raw_material_movement (reference);
CREATE INDEX IF NOT EXISTS idx_batch_branch_date    ON ${tenant_schema}.bakery_batch (branch_id, batch_date);
CREATE INDEX IF NOT EXISTS idx_batch_production     ON ${tenant_schema}.bakery_batch (production_id);
CREATE INDEX IF NOT EXISTS idx_batch_input_batch    ON ${tenant_schema}.bakery_batch_input (batch_id);
CREATE INDEX IF NOT EXISTS idx_batch_input_rm       ON ${tenant_schema}.bakery_batch_input (raw_material_id);
CREATE INDEX IF NOT EXISTS idx_batch_output_batch   ON ${tenant_schema}.bakery_batch_output (batch_id);
CREATE INDEX IF NOT EXISTS idx_batch_output_product ON ${tenant_schema}.bakery_batch_output (product_id);

-- ---------------------------------------------------------------------
-- 8) Rol PANADERO (tenant, patrón V9 con tenant_id explícito). Durante la
--    migración no hay app.current_tenant en la sesión de siembra, por eso se fija
--    el tenant_id explícitamente.
-- ---------------------------------------------------------------------
INSERT INTO ${tenant_schema}.role (tenant_id, code, name, system_role) VALUES
    ('${tenant_schema}', 'PANADERO', 'Panadero', true)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------
-- 9) Permisos del sub-módulo 'bakery_prod' (patrón V35). El PANADERO recibe SOLO
--    VIEW/CREATE (no edita ni cancela tandas). OWNER/ADMIN también sobre bakery_prod
--    (pueden capturar tandas). 'bakery_prod' hereda la habilitación comercial de
--    'bakery' en ModuleAccessEvaluator; no es un módulo comercial facturable propio.
-- ---------------------------------------------------------------------
INSERT INTO ${tenant_schema}.role_permission (tenant_id, role_id, module_key, action)
SELECT '${tenant_schema}', r.id, 'bakery_prod', a.action
FROM ${tenant_schema}.role r
CROSS JOIN (VALUES ('VIEW'),('CREATE')) AS a(action)
WHERE r.code = 'PANADERO'
ON CONFLICT (role_id, module_key, action) DO NOTHING;

INSERT INTO ${tenant_schema}.role_permission (tenant_id, role_id, module_key, action)
SELECT '${tenant_schema}', r.id, 'bakery_prod', a.action
FROM ${tenant_schema}.role r
CROSS JOIN (VALUES ('VIEW'),('CREATE')) AS a(action)
WHERE r.code IN ('OWNER','ADMIN')
ON CONFLICT (role_id, module_key, action) DO NOTHING;

-- ---------------------------------------------------------------------
-- 10) Row-Level Security en las 6 tablas nuevas (patrón V34 obligatorio).
--     DROP POLICY IF EXISTS para que la re-aplicación nunca falle.
-- ---------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['raw_material','raw_material_stock','raw_material_movement',
                             'bakery_batch','bakery_batch_input','bakery_batch_output']
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
