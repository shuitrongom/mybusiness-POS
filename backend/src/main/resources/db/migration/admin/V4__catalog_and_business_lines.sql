-- =====================================================================
-- Catálogos globales y motor multi-giro (schema admin).
--   - Catálogo SAT de productos/servicios y de unidades (obligatorio CFDI 4.0).
--   - Catálogo maestro compartido de productos (crece con el uso; beneficia a todos).
--   - Perfiles de giro y definición de campos dinámicos por giro.
-- =====================================================================

-- ---- Catálogo SAT de clave de producto o servicio (c_ClaveProdServ) ----
CREATE TABLE admin.sat_prod_serv (
    clave       VARCHAR(8)   PRIMARY KEY,
    descripcion VARCHAR(500) NOT NULL
);
COMMENT ON TABLE admin.sat_prod_serv IS 'Catálogo oficial del SAT de productos y servicios (c_ClaveProdServ).';

-- ---- Catálogo SAT de clave de unidad (c_ClaveUnidad) ----
CREATE TABLE admin.sat_unit (
    clave       VARCHAR(3)   PRIMARY KEY,
    nombre      VARCHAR(200) NOT NULL
);
COMMENT ON TABLE admin.sat_unit IS 'Catálogo oficial del SAT de unidades de medida (c_ClaveUnidad).';

-- ---- Perfiles de giro ----
-- Un giro define qué módulos sugerir por defecto y qué campos dinámicos aplican a sus productos.
CREATE TABLE admin.business_line (
    code            VARCHAR(60)  PRIMARY KEY,   -- abarrotes, materias_primas, panaderia, polleria...
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE admin.business_line IS 'Perfiles de giro. Un giro nuevo es configuración, no código.';

-- ---- Definición de campos dinámicos por giro ----
-- Describe atributos específicos de los productos de un giro (ej. caducidad, peso, calibre).
CREATE TABLE admin.business_line_field (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    line_code     VARCHAR(60)  NOT NULL REFERENCES admin.business_line(code) ON DELETE CASCADE,
    field_key     VARCHAR(60)  NOT NULL,        -- clave del atributo (ej. expiration_date)
    label         VARCHAR(120) NOT NULL,        -- etiqueta visible (ej. "Fecha de caducidad")
    data_type     VARCHAR(20)  NOT NULL,        -- STRING, NUMBER, DATE, BOOLEAN
    required      BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_line_field UNIQUE (line_code, field_key),
    CONSTRAINT chk_field_data_type CHECK (data_type IN ('STRING','NUMBER','DATE','BOOLEAN'))
);
COMMENT ON TABLE admin.business_line_field IS 'Campos dinámicos que aplican a los productos de un giro.';

-- ---- Módulos sugeridos por giro (valores por defecto al crear un negocio de ese giro) ----
CREATE TABLE admin.business_line_module (
    line_code  VARCHAR(60) NOT NULL REFERENCES admin.business_line(code) ON DELETE CASCADE,
    module_key VARCHAR(60) NOT NULL REFERENCES admin.module_catalog(module_key),
    PRIMARY KEY (line_code, module_key)
);

-- ---- Catálogo maestro compartido de productos ----
-- Crece con el uso: cuando un negocio da de alta un producto nuevo (por código de barras),
-- se sugiere guardarlo aquí para beneficio de futuros clientes. Es solo de sugerencia; cada
-- negocio mantiene su propio catálogo aislado en su schema.
CREATE TABLE admin.master_product (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barcode        VARCHAR(64)   UNIQUE,
    name           VARCHAR(300)  NOT NULL,
    brand          VARCHAR(160),
    category       VARCHAR(120),
    unit           VARCHAR(40),                 -- unidad común (pieza, kg, litro...)
    sat_prod_serv  VARCHAR(8)    REFERENCES admin.sat_prod_serv(clave),
    sat_unit       VARCHAR(3)    REFERENCES admin.sat_unit(clave),
    source         VARCHAR(20)   NOT NULL DEFAULT 'SEED',  -- SEED (curado) o USER (aportado)
    times_seen     INTEGER       NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_master_source CHECK (source IN ('SEED','USER'))
);
COMMENT ON TABLE admin.master_product IS 'Catálogo maestro compartido de productos; crece con el uso.';

CREATE INDEX idx_master_product_name ON admin.master_product (lower(name));
CREATE INDEX idx_master_product_barcode ON admin.master_product (barcode);

-- Permisos para el rol de aplicación sobre las nuevas tablas del schema admin.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'pos_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA admin TO pos_app';
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA admin TO pos_app';
    END IF;
END
$$;
