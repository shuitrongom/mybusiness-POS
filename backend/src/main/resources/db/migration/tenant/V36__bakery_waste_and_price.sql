-- =====================================================================
-- MyBusiness Silva — V36 (tenant): PRECIO DE MERMA + EXISTENCIA Y
-- TRAZABILIDAD DE MERMA INTEGRADAS.
--
-- Número de versión: V36. La más alta tenant es V35__bakery_rbac_permissions.
--   No existe V36 => esta migración toma V36. (Reconfirmar listando el dir.)
--
-- Rediseño integrado de la merma (spec inventario-panaderia-integrado):
--   1) product.waste_price: precio FIJO de merma por artículo (pestaña Precios).
--   2) bakery_waste_stock: existencia EN MERMA por producto/sucursal, para poder
--      vender merma y ARRASTRARLA entre días sin descuadrar el stock normal.
--   3) bakery_waste_movement: trazabilidad de CADA transición/venta de merma, con
--      el ORIGEN (CIERRE automático al cerrar caja, MANUAL en salidas, VENTA en el
--      POS, REPARTO a repartidor). Reemplaza funcionalmente a bakery_waste_sale.
--   4) concepto de salida configurable "se dio a repartidor" (inventory_concept).
--
-- Sin huérfanas: bakery_waste_sale (V34) solo registraba la venta de merma y nunca
-- se usó productivamente (el flujo no existía); se elimina aquí porque su rol lo
-- cubre bakery_waste_movement. DROP TABLE IF EXISTS para idempotencia.
--
-- Idempotente: ADD COLUMN IF NOT EXISTS, CREATE TABLE/INDEX IF NOT EXISTS,
-- INSERT ... ON CONFLICT DO NOTHING, RLS con DROP POLICY IF EXISTS.
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

-- ---------------------------------------------------------------------
-- 1) Precio de merma por artículo (fijo). Default 0 inocuo para otros giros.
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.product
    ADD COLUMN IF NOT EXISTS waste_price NUMERIC(12,2) NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 2) Existencia EN MERMA por producto/sucursal (contador, se arrastra entre días).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.bakery_waste_stock (
    id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    product_id BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id) ON DELETE CASCADE,
    branch_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    quantity   NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_bakery_waste_stock UNIQUE (product_id, branch_id)
);

-- ---------------------------------------------------------------------
-- 3) Trazabilidad de merma: una fila por transición o venta de merma.
--    origin: CIERRE | MANUAL | VENTA | REPARTO
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.bakery_waste_movement (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    product_id   BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    branch_id    BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    origin       VARCHAR(16)   NOT NULL CHECK (origin IN ('CIERRE','MANUAL','VENTA','REPARTO')),
    quantity     NUMERIC(14,3) NOT NULL,
    normal_price NUMERIC(14,2) NOT NULL DEFAULT 0,   -- precio normal de referencia
    waste_price  NUMERIC(14,2) NOT NULL DEFAULT 0,   -- precio de merma aplicado/registrado
    difference   NUMERIC(14,2) NOT NULL DEFAULT 0,   -- (normal - merma) * cantidad, pérdida
    cashier      VARCHAR(255),
    sale_id      BIGINT,                             -- venta ligada (solo origin VENTA)
    shift_id     BIGINT,                             -- turno ligado (origin CIERRE/MANUAL)
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bakery_waste_stock_prod  ON ${tenant_schema}.bakery_waste_stock (product_id);
CREATE INDEX IF NOT EXISTS idx_bakery_waste_mov_prod    ON ${tenant_schema}.bakery_waste_movement (product_id, created_at);
CREATE INDEX IF NOT EXISTS idx_bakery_waste_mov_origin  ON ${tenant_schema}.bakery_waste_movement (origin, created_at);
CREATE INDEX IF NOT EXISTS idx_bakery_waste_mov_sale    ON ${tenant_schema}.bakery_waste_movement (sale_id);

-- ---------------------------------------------------------------------
-- 4) Concepto de salida configurable para el reparto de merma.
--    Reutiliza inventory_concept (catálogo de motivos IN/OUT de V21), que ya
--    trae "Salida por merma" (OUT) y "Producción / ensamble" (IN). Aquí se
--    agrega el motivo específico de reparto de merma. La tabla no tiene UNIQUE
--    en name, así que el alta es condicional (WHERE NOT EXISTS) para idempotencia.
-- ---------------------------------------------------------------------
INSERT INTO ${tenant_schema}.inventory_concept (name, direction, active)
SELECT 'Merma: se dio a repartidor', 'OUT', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM ${tenant_schema}.inventory_concept
    WHERE name = 'Merma: se dio a repartidor'
);

-- ---------------------------------------------------------------------
-- 5) Row-Level Security de las tablas nuevas (idempotente).
-- ---------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['bakery_waste_stock','bakery_waste_movement']
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', '${tenant_schema}', t);
        EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', '${tenant_schema}', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation_%I ON %I.%I', t, '${tenant_schema}', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation_%I ON %I.%I USING (tenant_id = current_setting(''app.current_tenant'', true)) WITH CHECK (tenant_id = current_setting(''app.current_tenant'', true))',
            t, '${tenant_schema}', t);
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- 6) Retirar bakery_waste_sale (V34), reemplazada por bakery_waste_movement.
--    Nunca se usó productivamente; se elimina para no dejar tabla huérfana.
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS ${tenant_schema}.bakery_waste_sale;
