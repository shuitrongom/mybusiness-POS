-- =====================================================================
-- MyBusiness Silva — V21 (tenant): SUITE DE INVENTARIO nivel ENTERPRISE.
--
-- Módulo 3. La suite más transversal. Aporta lo que faltaba para superar a los
-- POS líderes en control de inventario:
--   • Números de SERIE (electrónica, con puerto de entrada y pedimento aduanal).
--   • LOTES con caducidad (ya existía product_lot; se refuerza).
--   • VARIANTES por TALLA y COLOR (ropa/calzado): un producto por combinación.
--   • FAMILIAS y subfamilias (jerarquía de categorías).
--   • DOCUMENTOS de entrada/salida con conceptos (motivos) y renglones.
--   • CONTEO FÍSICO (inventario físico) con marbetes, capturas y diferencias.
--   • Etiquetas de código de barras (formatos).
--
-- Idempotente donde amplía. Flyway sustituye ${tenant_schema}.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Banderas y clasificación en producto
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS track_serial   BOOLEAN NOT NULL DEFAULT FALSE; -- control de números de serie
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS model_id       BIGINT;  -- modelo del que es variante (ropa/calzado)
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS size_code      VARCHAR(20);  -- talla de la variante
ALTER TABLE ${tenant_schema}.product ADD COLUMN IF NOT EXISTS color_code     VARCHAR(20);  -- color de la variante

-- ---------------------------------------------------------------------
-- 2) Familias / subfamilias (jerarquía de categorías)
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.category ADD COLUMN IF NOT EXISTS parent_id BIGINT REFERENCES ${tenant_schema}.category(id);

-- ---------------------------------------------------------------------
-- 3) Catálogo de tallas y colores
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.size_catalog (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    code       VARCHAR(20)  NOT NULL,
    label      VARCHAR(60)  NOT NULL,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_size_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS ${tenant_schema}.color_catalog (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    code       VARCHAR(20)  NOT NULL,
    label      VARCHAR(60)  NOT NULL,
    hex        VARCHAR(9),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_color_code UNIQUE (code)
);

-- Semillas de tallas y colores comunes.
INSERT INTO ${tenant_schema}.size_catalog (code, label, sort_order) VALUES
    ('CH','Chica',1),('M','Mediana',2),('G','Grande',3),('XG','Extra grande',4),
    ('28','28',10),('30','30',11),('32','32',12),('34','34',13),('36','36',14),('38','38',15)
ON CONFLICT (code) DO NOTHING;

INSERT INTO ${tenant_schema}.color_catalog (code, label, hex) VALUES
    ('BLA','Blanco','#ffffff'),('NEG','Negro','#111111'),('AZU','Azul','#2563eb'),
    ('ROJ','Rojo','#dc2626'),('VER','Verde','#16a34a'),('GRI','Gris','#6b7280')
ON CONFLICT (code) DO NOTHING;

-- Modelo del que cuelgan las variantes (talla x color).
CREATE TABLE IF NOT EXISTS ${tenant_schema}.product_model (
    id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    code       VARCHAR(60)   NOT NULL,
    name       VARCHAR(200)  NOT NULL,
    brand      VARCHAR(120),
    cost       NUMERIC(12,2) NOT NULL DEFAULT 0,
    price1     NUMERIC(12,2) NOT NULL DEFAULT 0,
    price2     NUMERIC(12,2) NOT NULL DEFAULT 0,
    price3     NUMERIC(12,2) NOT NULL DEFAULT 0,
    category_id BIGINT       REFERENCES ${tenant_schema}.category(id),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_model_code UNIQUE (code)
);

-- ---------------------------------------------------------------------
-- 4) Conceptos de entrada/salida de inventario
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.inventory_concept (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    name       VARCHAR(120) NOT NULL,
    direction  VARCHAR(4)   NOT NULL,  -- IN, OUT
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_concept_dir CHECK (direction IN ('IN','OUT'))
);

INSERT INTO ${tenant_schema}.inventory_concept (name, direction) VALUES
    ('Entrada por ajuste', 'IN'),
    ('Devolución de cliente', 'IN'),
    ('Producción / ensamble', 'IN'),
    ('Bonificación de proveedor', 'IN'),
    ('Salida por merma', 'OUT'),
    ('Salida por consumo interno', 'OUT'),
    ('Salida por caducidad', 'OUT'),
    ('Salida por robo / extravío', 'OUT')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 5) Documentos de entrada/salida de inventario (con renglones)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.inventory_document (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    folio       VARCHAR(40),
    doc_type    VARCHAR(4)    NOT NULL,  -- IN, OUT
    concept_id  BIGINT        REFERENCES ${tenant_schema}.inventory_concept(id),
    concept_name VARCHAR(120),
    branch_id   BIGINT        NOT NULL REFERENCES ${tenant_schema}.branch(id),
    total_qty   NUMERIC(14,3) NOT NULL DEFAULT 0,
    total_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    notes       VARCHAR(500),
    created_by  VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_invdoc_type CHECK (doc_type IN ('IN','OUT'))
);

CREATE TABLE IF NOT EXISTS ${tenant_schema}.inventory_document_line (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    document_id BIGINT        NOT NULL REFERENCES ${tenant_schema}.inventory_document(id) ON DELETE CASCADE,
    product_id  BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    description VARCHAR(300),
    quantity    NUMERIC(14,3) NOT NULL,
    unit_cost   NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total  NUMERIC(14,2) NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------
-- 6) Números de serie
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.product_serial (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    product_id   BIGINT       NOT NULL REFERENCES ${tenant_schema}.product(id),
    branch_id    BIGINT       REFERENCES ${tenant_schema}.branch(id),
    serial       VARCHAR(120) NOT NULL,
    status       VARCHAR(12)  NOT NULL DEFAULT 'IN_STOCK',  -- IN_STOCK, SOLD, RETURNED
    entry_port   VARCHAR(120),   -- puerto de entrada (importación)
    pedimento    VARCHAR(60),    -- número de pedimento aduanal
    document_ref VARCHAR(120),   -- documento que la originó
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_serial UNIQUE (serial),
    CONSTRAINT chk_serial_status CHECK (status IN ('IN_STOCK','SOLD','RETURNED'))
);

-- ---------------------------------------------------------------------
-- 7) Lotes: reforzar la tabla existente product_lot con puerto/pedimento
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.product_lot ADD COLUMN IF NOT EXISTS entry_port   VARCHAR(120);
ALTER TABLE ${tenant_schema}.product_lot ADD COLUMN IF NOT EXISTS pedimento    VARCHAR(60);
ALTER TABLE ${tenant_schema}.product_lot ADD COLUMN IF NOT EXISTS document_ref VARCHAR(120);

-- ---------------------------------------------------------------------
-- 8) Inventario físico (conteo)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.physical_count (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    folio         VARCHAR(40),
    branch_id     BIGINT       NOT NULL REFERENCES ${tenant_schema}.branch(id),
    category_id   BIGINT       REFERENCES ${tenant_schema}.category(id),  -- alcance por familia (opcional)
    status        VARCHAR(12)  NOT NULL DEFAULT 'OPEN',  -- OPEN, APPLIED, CANCELLED
    created_by    VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    applied_at    TIMESTAMPTZ,
    CONSTRAINT chk_count_status CHECK (status IN ('OPEN','APPLIED','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS ${tenant_schema}.physical_count_line (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    count_id      BIGINT        NOT NULL REFERENCES ${tenant_schema}.physical_count(id) ON DELETE CASCADE,
    product_id    BIGINT        NOT NULL REFERENCES ${tenant_schema}.product(id),
    marbete       VARCHAR(40),                       -- número de marbete/etiqueta de conteo
    theoretical   NUMERIC(14,3) NOT NULL DEFAULT 0,  -- existencia teórica al iniciar
    counted       NUMERIC(14,3),                     -- existencia real contada
    difference    NUMERIC(14,3) NOT NULL DEFAULT 0,  -- counted - theoretical
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_count_product UNIQUE (count_id, product_id)
);

-- ---------------------------------------------------------------------
-- 9) Formatos de etiqueta de código de barras
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.label_format (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)  NOT NULL DEFAULT current_setting('app.current_tenant', true),
    name        VARCHAR(80)  NOT NULL,
    width_mm    INTEGER      NOT NULL DEFAULT 50,
    height_mm   INTEGER      NOT NULL DEFAULT 30,
    show_price  BOOLEAN      NOT NULL DEFAULT TRUE,
    show_name   BOOLEAN      NOT NULL DEFAULT TRUE,
    columns     INTEGER      NOT NULL DEFAULT 3,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO ${tenant_schema}.label_format (name, width_mm, height_mm, columns) VALUES
    ('Etiqueta chica 40x25', 40, 25, 4),
    ('Etiqueta mediana 50x30', 50, 30, 3),
    ('Etiqueta de anaquel 70x40', 70, 40, 2)
ON CONFLICT DO NOTHING;

-- Índices
CREATE INDEX IF NOT EXISTS idx_serial_product   ON ${tenant_schema}.product_serial (product_id);
CREATE INDEX IF NOT EXISTS idx_serial_status    ON ${tenant_schema}.product_serial (status);
CREATE INDEX IF NOT EXISTS idx_invdoc_branch    ON ${tenant_schema}.inventory_document (branch_id, created_at);
CREATE INDEX IF NOT EXISTS idx_invdocline_doc   ON ${tenant_schema}.inventory_document_line (document_id);
CREATE INDEX IF NOT EXISTS idx_count_branch     ON ${tenant_schema}.physical_count (branch_id);
CREATE INDEX IF NOT EXISTS idx_countline_count  ON ${tenant_schema}.physical_count_line (count_id);
CREATE INDEX IF NOT EXISTS idx_product_model    ON ${tenant_schema}.product (model_id);
CREATE INDEX IF NOT EXISTS idx_category_parent  ON ${tenant_schema}.category (parent_id);

-- ---------------------------------------------------------------------
-- Row-Level Security en las tablas nuevas.
-- ---------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['size_catalog','color_catalog','product_model','inventory_concept',
                             'inventory_document','inventory_document_line','product_serial',
                             'physical_count','physical_count_line','label_format']
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
