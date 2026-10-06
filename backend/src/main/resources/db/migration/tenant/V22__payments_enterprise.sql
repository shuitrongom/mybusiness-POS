-- =====================================================================
-- MyBusiness Silva — V22 (tenant): SUITE DE RECARGAS Y PAGO DE SERVICIOS.
--
-- Módulo 4. Modela una corresponsalía prepago (como OXXO/UnDosTres/TAECEL):
--   • SALDO PREPAGADO (bolsa) del negocio: cada recarga/pago lo descuenta.
--   • ABONOS/DEPÓSITOS: el negocio deposita a la plataforma y reporta el
--     depósito; al aprobarse, suma al saldo.
--   • CATÁLOGO REAL de compañías (recargas) y servicios de México, con la
--     comisión que gana el negocio por transacción y los montos permitidos.
--
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Saldo prepagado del negocio (una fila por tenant)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.payment_balance (
    id          SMALLINT      PRIMARY KEY DEFAULT 1,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    balance     NUMERIC(14,2) NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_balance_singleton CHECK (id = 1)
);
INSERT INTO ${tenant_schema}.payment_balance (id, balance) VALUES (1, 0)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 2) Abonos / depósitos reportados por el negocio
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.payment_deposit (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    pos_id      VARCHAR(60),                 -- ID POS / terminal
    name        VARCHAR(200)  NOT NULL,      -- quién reporta
    email       VARCHAR(255),
    bank        VARCHAR(60)   NOT NULL,      -- Banamex, BBVA, Santander, etc.
    account     VARCHAR(60),
    reference   VARCHAR(120)  NOT NULL,      -- referencia/folio del depósito
    amount      NUMERIC(14,2) NOT NULL,
    pay_date    DATE          NOT NULL,
    comments    VARCHAR(500),
    status      VARCHAR(12)   NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMPTZ,
    created_by  VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_deposit_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);

-- ---------------------------------------------------------------------
-- 3) Catálogo de compañías (recargas) y servicios (pagos)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ${tenant_schema}.payment_catalog (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        VARCHAR(63)   NOT NULL DEFAULT current_setting('app.current_tenant', true),
    op_type          VARCHAR(15)   NOT NULL,  -- RECHARGE, SERVICE
    category         VARCHAR(60)   NOT NULL,  -- Telefonía, Luz, Agua, Gas, TV e Internet, Gobierno, Créditos, Otros
    provider         VARCHAR(120)  NOT NULL,  -- Telcel, CFE, Izzi, Infonavit...
    commission_pct   NUMERIC(6,4)  NOT NULL DEFAULT 0,   -- comisión del negocio en fracción (0.03 = 3%)
    commission_fixed NUMERIC(10,2) NOT NULL DEFAULT 0,   -- comisión fija por transacción
    min_amount       NUMERIC(10,2) NOT NULL DEFAULT 0,
    max_amount       NUMERIC(10,2) NOT NULL DEFAULT 0,    -- 0 = sin tope
    fixed_amounts    VARCHAR(200),                        -- montos sugeridos "10,20,30,50,100,200"
    reference_label  VARCHAR(60)   NOT NULL DEFAULT 'Referencia',  -- etiqueta del campo (Teléfono, No. de servicio...)
    brand_color      VARCHAR(9),                          -- color oficial de la marca (para el logo)
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order       INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT chk_catalog_type CHECK (op_type IN ('RECHARGE','SERVICE'))
);

-- ---------------------------------------------------------------------
-- 4) Ampliar payment_operation con saldo y costo
-- ---------------------------------------------------------------------
ALTER TABLE ${tenant_schema}.payment_operation ADD COLUMN IF NOT EXISTS catalog_id     BIGINT;
ALTER TABLE ${tenant_schema}.payment_operation ADD COLUMN IF NOT EXISTS category       VARCHAR(60);
ALTER TABLE ${tenant_schema}.payment_operation ADD COLUMN IF NOT EXISTS cost           NUMERIC(14,2) NOT NULL DEFAULT 0;  -- lo que descuenta del saldo (monto - comisión)
ALTER TABLE ${tenant_schema}.payment_operation ADD COLUMN IF NOT EXISTS customer_charge NUMERIC(14,2) NOT NULL DEFAULT 0; -- lo que se cobra al cliente
ALTER TABLE ${tenant_schema}.payment_operation ADD COLUMN IF NOT EXISTS balance_after  NUMERIC(14,2) NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- Semillas: catálogo REAL de México
-- ---------------------------------------------------------------------
-- Recargas de tiempo aire (todas las compañías/OMV que operan en México), con su color de marca.
INSERT INTO ${tenant_schema}.payment_catalog
    (op_type, category, provider, commission_pct, commission_fixed, min_amount, max_amount, fixed_amounts, reference_label, brand_color, sort_order)
VALUES
    ('RECHARGE','Telefonía','Telcel',        0.03, 0, 10, 1500, '10,20,30,50,100,150,200,300,500', 'Teléfono a 10 dígitos', '#0093d0', 1),
    ('RECHARGE','Telefonía','Movistar',      0.04, 0, 10, 1000, '10,20,30,50,100,150,200,300',     'Teléfono a 10 dígitos', '#00a9e0', 2),
    ('RECHARGE','Telefonía','AT&T',          0.04, 0, 10, 1000, '10,20,30,50,100,150,200,300',     'Teléfono a 10 dígitos', '#00a8e0', 3),
    ('RECHARGE','Telefonía','Unefon',        0.05, 0, 10, 500,  '10,20,30,50,100,150,200',         'Teléfono a 10 dígitos', '#e2001a', 4),
    ('RECHARGE','Telefonía','Bait',          0.06, 0, 10, 500,  '10,20,30,50,100,150,200',         'Teléfono a 10 dígitos', '#e30613', 5),
    ('RECHARGE','Telefonía','Virgin Mobile', 0.05, 0, 10, 500,  '10,20,30,50,100,150,200',         'Teléfono a 10 dígitos', '#e10a0a', 6),
    ('RECHARGE','Telefonía','OUI Móvil',     0.05, 0, 10, 500,  '10,20,30,50,100,150',             'Teléfono a 10 dígitos', '#ff6a13', 7),
    ('RECHARGE','Telefonía','Pillofon',      0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#7b2ff7', 8),
    ('RECHARGE','Telefonía','Diri Móvil',    0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#ec008c', 9),
    ('RECHARGE','Telefonía','Soriana Móvil', 0.05, 0, 10, 500,  '10,20,30,50,100,150',             'Teléfono a 10 dígitos', '#d5121a', 10),
    ('RECHARGE','Telefonía','FreedomPop',    0.05, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#00b3a4', 11),
    ('RECHARGE','Telefonía','Weex',          0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#00d1b2', 12),
    ('RECHARGE','Telefonía','Flash Mobile',  0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#f7941e', 13),
    ('RECHARGE','Servicios TV','Sky',        0.02, 0, 50, 2000, '69,99,199,299,399,499', 'Tarjeta Sky',  '#e2001a', 20),
    ('RECHARGE','Servicios TV','Dish',       0.02, 0, 50, 2000, '50,100,200,300,500',    'Tarjeta Dish', '#ec1c24', 21)
ON CONFLICT DO NOTHING;

-- Pago de servicios (los que se pueden pagar en tienda de conveniencia / corresponsalía), con color.
INSERT INTO ${tenant_schema}.payment_catalog
    (op_type, category, provider, commission_pct, commission_fixed, min_amount, max_amount, reference_label, brand_color, sort_order)
VALUES
    ('SERVICE','Luz','CFE',                         0, 8,  1, 0, 'No. de servicio (RPU)', '#009540', 30),
    ('SERVICE','Agua','Agua (organismo local)',     0, 8,  1, 0, 'No. de cuenta',         '#0088c7', 31),
    ('SERVICE','Gas','Naturgy',                     0, 8,  1, 0, 'No. de contrato',       '#ff7a00', 32),
    ('SERVICE','Gas','Gas Express (LP)',            0, 8,  1, 0, 'No. de cliente',        '#e30613', 33),
    ('SERVICE','Telefonía','Telmex',                0, 8,  1, 0, 'No. de teléfono',       '#005baa', 34),
    ('SERVICE','TV e Internet','Izzi',              0, 8,  1, 0, 'No. de cuenta',         '#00b0e6', 35),
    ('SERVICE','TV e Internet','Totalplay',         0, 8,  1, 0, 'No. de contrato',       '#e2231a', 36),
    ('SERVICE','TV e Internet','Megacable',         0, 8,  1, 0, 'No. de suscriptor',     '#ee2a24', 37),
    ('SERVICE','TV e Internet','Sky',               0, 8,  1, 0, 'No. de tarjeta',        '#e2001a', 38),
    ('SERVICE','TV e Internet','Dish',              0, 8,  1, 0, 'No. de cuenta',         '#ec1c24', 39),
    ('SERVICE','TV e Internet','Star TV',           0, 8,  1, 0, 'No. de cuenta',         '#1478bd', 40),
    ('SERVICE','TV e Internet','Axtel',             0, 8,  1, 0, 'No. de cuenta',         '#00a19a', 41),
    ('SERVICE','Gobierno','Predial (municipal)',    0, 10, 1, 0, 'Clave catastral',       '#6b7280', 50),
    ('SERVICE','Gobierno','Tenencia / Refrendo',    0, 10, 1, 0, 'Placa o No. de control','#6b7280', 51),
    ('SERVICE','Gobierno','Agua (CONAGUA)',         0, 10, 1, 0, 'No. de referencia',     '#0088c7', 52),
    ('SERVICE','Gobierno','Infonavit',              0, 12, 1, 0, 'No. de crédito',        '#b01e2e', 53),
    ('SERVICE','Gobierno','Fonacot',                0, 12, 1, 0, 'No. de crédito',        '#611232', 54),
    ('SERVICE','Gobierno','SAT (DPA)',              0, 12, 1, 0, 'Línea de captura',      '#611232', 55),
    ('SERVICE','Créditos','Coppel',                 0, 10, 1, 0, 'No. de cliente',        '#004a97', 60),
    ('SERVICE','Créditos','Elektra',                0, 10, 1, 0, 'No. de cuenta',         '#004a97', 61),
    ('SERVICE','Créditos','Banco Azteca',           0, 10, 1, 0, 'No. de cuenta',         '#00a94f', 62),
    ('SERVICE','Créditos','Famsa',                  0, 10, 1, 0, 'No. de cuenta',         '#e30613', 63),
    ('SERVICE','Créditos','BBVA',                   0, 12, 1, 0, 'No. de tarjeta',        '#004481', 64),
    ('SERVICE','Créditos','Santander',              0, 12, 1, 0, 'No. de tarjeta',        '#ec0000', 65),
    ('SERVICE','Créditos','Banorte',                0, 12, 1, 0, 'No. de tarjeta',        '#eb0029', 66),
    ('SERVICE','Streaming','Netflix',               0.03, 0, 50, 1000, 'Monto',           '#e50914', 70),
    ('SERVICE','Streaming','Spotify',               0.03, 0, 50, 1000, 'Monto',           '#1db954', 71),
    ('SERVICE','Otros','Transporte',                0, 5,  1, 0, 'No. de tarjeta',        '#f59e0b', 80),
    ('SERVICE','Otros','Peaje TAG (Televía/IAVE)',  0, 8,  1, 0, 'No. de TAG',            '#0ea5e9', 81)
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_deposit_status  ON ${tenant_schema}.payment_deposit (status);
CREATE INDEX IF NOT EXISTS idx_catalog_type    ON ${tenant_schema}.payment_catalog (op_type, category);

-- Row-Level Security en las tablas nuevas.
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['payment_balance','payment_deposit','payment_catalog']
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
