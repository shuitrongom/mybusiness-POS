-- =====================================================================
-- MyBusiness Silva — V23 (tenant): CATÁLOGO COMPLETO de recargas y servicios
-- enfocado a México (nacional + CDMX + Estado de México / Toluca).
--
-- Se aplica ENCIMA de V22 sin borrar nada. Usa un índice único por
-- (op_type, provider) para poder insertar con ON CONFLICT DO NOTHING y no
-- duplicar lo ya sembrado. Todas las filas traen su color de marca.
--
-- Flyway sustituye ${tenant_schema}.
-- =====================================================================

-- Asegura la columna de color de marca (en V22 pudo crearse la tabla sin ella).
ALTER TABLE ${tenant_schema}.payment_catalog ADD COLUMN IF NOT EXISTS brand_color VARCHAR(9);

-- Índice único que habilita el ON CONFLICT idempotente (evita duplicados por proveedor).
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalog_provider
    ON ${tenant_schema}.payment_catalog (op_type, provider);

-- ---------------------------------------------------------------------
-- RECARGAS: todas las compañías y OMV que operan en México
-- ---------------------------------------------------------------------
INSERT INTO ${tenant_schema}.payment_catalog
    (op_type, category, provider, commission_pct, commission_fixed, min_amount, max_amount, fixed_amounts, reference_label, brand_color, sort_order)
VALUES
    ('RECHARGE','Telefonía','Telcel',            0.03, 0, 10, 1500, '10,20,30,50,100,150,200,300,500', 'Teléfono a 10 dígitos', '#0093d0', 1),
    ('RECHARGE','Telefonía','Movistar',          0.04, 0, 10, 1000, '10,20,30,50,100,150,200,300',     'Teléfono a 10 dígitos', '#00a9e0', 2),
    ('RECHARGE','Telefonía','AT&T',              0.04, 0, 10, 1000, '10,20,30,50,100,150,200,300',     'Teléfono a 10 dígitos', '#00a8e0', 3),
    ('RECHARGE','Telefonía','Unefon',            0.05, 0, 10, 500,  '10,20,30,50,100,150,200',         'Teléfono a 10 dígitos', '#e2001a', 4),
    ('RECHARGE','Telefonía','Bait',              0.06, 0, 10, 500,  '10,20,30,50,100,150,200',         'Teléfono a 10 dígitos', '#e30613', 5),
    ('RECHARGE','Telefonía','Virgin Mobile',     0.05, 0, 10, 500,  '10,20,30,50,100,150,200',         'Teléfono a 10 dígitos', '#e10a0a', 6),
    ('RECHARGE','Telefonía','OUI Móvil',         0.05, 0, 10, 500,  '10,20,30,50,100,150',             'Teléfono a 10 dígitos', '#ff6a13', 7),
    ('RECHARGE','Telefonía','Pillofon',          0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#7b2ff7', 8),
    ('RECHARGE','Telefonía','Diri Móvil',        0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#ec008c', 9),
    ('RECHARGE','Telefonía','Soriana Móvil',     0.05, 0, 10, 500,  '10,20,30,50,100,150',             'Teléfono a 10 dígitos', '#d5121a', 10),
    ('RECHARGE','Telefonía','FreedomPop',        0.05, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#00b3a4', 11),
    ('RECHARGE','Telefonía','Weex',              0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#00d1b2', 12),
    ('RECHARGE','Telefonía','Flash Mobile',      0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#f7941e', 13),
    ('RECHARGE','Telefonía','Cierto Móvil',      0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#22c55e', 14),
    ('RECHARGE','Telefonía','Telcel Sin Límite', 0.03, 0, 10, 1500, '50,100,150,200,300,500',          'Teléfono a 10 dígitos', '#0093d0', 15),
    ('RECHARGE','Telefonía','Aló (Chedraui)',    0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#e30613', 16),
    ('RECHARGE','Telefonía','maxcom Móvil',      0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#0055a5', 17),
    ('RECHARGE','Telefonía','Simpati (Walmart)', 0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#0071ce', 18),
    ('RECHARGE','Telefonía','QBOcel',            0.06, 0, 10, 500,  '10,20,30,50,100',                 'Teléfono a 10 dígitos', '#ff5a00', 19),
    ('RECHARGE','Servicios TV','Sky',            0.02, 0, 50, 2000, '69,99,199,299,399,499',           'Tarjeta Sky',  '#e2001a', 20),
    ('RECHARGE','Servicios TV','Dish',           0.02, 0, 50, 2000, '50,100,200,300,500',              'Tarjeta Dish', '#ec1c24', 21)
ON CONFLICT (op_type, provider) DO NOTHING;

-- ---------------------------------------------------------------------
-- SERVICIOS: nacional + CDMX + Estado de México (Toluca)
-- ---------------------------------------------------------------------
INSERT INTO ${tenant_schema}.payment_catalog
    (op_type, category, provider, commission_pct, commission_fixed, min_amount, max_amount, reference_label, brand_color, sort_order)
VALUES
    -- Luz
    ('SERVICE','Luz','CFE',                              0, 8,  1, 0, 'No. de servicio (RPU)',   '#009540', 30),
    -- Agua nacional + organismos CDMX/Edomex
    ('SERVICE','Agua','Agua (organismo local)',          0, 8,  1, 0, 'No. de cuenta',           '#0088c7', 31),
    ('SERVICE','Agua','SACMEX (Agua CDMX)',              0, 8,  1, 0, 'Cuenta / toma',           '#0088c7', 32),
    ('SERVICE','Agua','Agua de Toluca',                  0, 8,  1, 0, 'No. de cuenta',           '#0088c7', 33),
    ('SERVICE','Agua','CAEM (Edomex)',                   0, 8,  1, 0, 'No. de cuenta',           '#0088c7', 34),
    -- Gas
    ('SERVICE','Gas','Naturgy',                          0, 8,  1, 0, 'No. de contrato',         '#ff7a00', 35),
    ('SERVICE','Gas','Gas Express (LP)',                 0, 8,  1, 0, 'No. de cliente',          '#e30613', 36),
    ('SERVICE','Gas','Gas Natural del Noreste',          0, 8,  1, 0, 'No. de contrato',         '#ff7a00', 37),
    -- Telefonía fija / internet
    ('SERVICE','Telefonía','Telmex',                     0, 8,  1, 0, 'No. de teléfono',         '#005baa', 38),
    ('SERVICE','TV e Internet','Izzi',                   0, 8,  1, 0, 'No. de cuenta',           '#00b0e6', 39),
    ('SERVICE','TV e Internet','Totalplay',              0, 8,  1, 0, 'No. de contrato',         '#e2231a', 40),
    ('SERVICE','TV e Internet','Megacable',              0, 8,  1, 0, 'No. de suscriptor',       '#ee2a24', 41),
    ('SERVICE','TV e Internet','Sky',                    0, 8,  1, 0, 'No. de tarjeta',          '#e2001a', 42),
    ('SERVICE','TV e Internet','Dish',                   0, 8,  1, 0, 'No. de cuenta',           '#ec1c24', 43),
    ('SERVICE','TV e Internet','Star TV',                0, 8,  1, 0, 'No. de cuenta',           '#1478bd', 44),
    ('SERVICE','TV e Internet','Axtel',                  0, 8,  1, 0, 'No. de cuenta',           '#00a19a', 45),
    ('SERVICE','TV e Internet','TotalPlay Empresarial',  0, 8,  1, 0, 'No. de contrato',         '#e2231a', 46),
    -- Gobierno nacional + CDMX + Edomex/Toluca
    ('SERVICE','Gobierno','Predial CDMX',                0, 10, 1, 0, 'Cuenta predial',          '#9d2449', 50),
    ('SERVICE','Gobierno','Predial Toluca',              0, 10, 1, 0, 'Clave catastral',         '#6b7280', 51),
    ('SERVICE','Gobierno','Predial (otro municipio)',    0, 10, 1, 0, 'Clave catastral',         '#6b7280', 52),
    ('SERVICE','Gobierno','Tenencia CDMX',               0, 10, 1, 0, 'Placa / No. de control',  '#9d2449', 53),
    ('SERVICE','Gobierno','Tenencia Edomex',             0, 10, 1, 0, 'Placa / No. de control',  '#0f4c81', 54),
    ('SERVICE','Gobierno','Refrendo / Tarjeta circulación', 0, 10, 1, 0, 'Placa',                '#0f4c81', 55),
    ('SERVICE','Gobierno','Infracciones CDMX',           0, 10, 1, 0, 'Folio de infracción',     '#9d2449', 56),
    ('SERVICE','Gobierno','Agua (CONAGUA)',              0, 10, 1, 0, 'No. de referencia',       '#0088c7', 57),
    ('SERVICE','Gobierno','Infonavit',                   0, 12, 1, 0, 'No. de crédito',          '#b01e2e', 58),
    ('SERVICE','Gobierno','Fonacot',                     0, 12, 1, 0, 'No. de crédito',          '#611232', 59),
    ('SERVICE','Gobierno','SAT (DPA)',                   0, 12, 1, 0, 'Línea de captura',        '#611232', 60),
    ('SERVICE','Gobierno','IMSS (cuotas)',               0, 12, 1, 0, 'Línea de captura',        '#026937', 61),
    -- Créditos y tiendas
    ('SERVICE','Créditos','Coppel',                      0, 10, 1, 0, 'No. de cliente',          '#004a97', 62),
    ('SERVICE','Créditos','Elektra',                     0, 10, 1, 0, 'No. de cuenta',           '#004a97', 63),
    ('SERVICE','Créditos','Banco Azteca',                0, 10, 1, 0, 'No. de cuenta',           '#00a94f', 64),
    ('SERVICE','Créditos','Famsa',                       0, 10, 1, 0, 'No. de cuenta',           '#e30613', 65),
    ('SERVICE','Créditos','Liverpool',                   0, 10, 1, 0, 'No. de tarjeta',          '#e5007d', 66),
    ('SERVICE','Créditos','Palacio de Hierro',           0, 10, 1, 0, 'No. de tarjeta',          '#111111', 67),
    ('SERVICE','Créditos','BBVA',                        0, 12, 1, 0, 'No. de tarjeta',          '#004481', 68),
    ('SERVICE','Créditos','Santander',                   0, 12, 1, 0, 'No. de tarjeta',          '#ec0000', 69),
    ('SERVICE','Créditos','Banorte',                     0, 12, 1, 0, 'No. de tarjeta',          '#eb0029', 70),
    ('SERVICE','Créditos','HSBC',                        0, 12, 1, 0, 'No. de tarjeta',          '#db0011', 71),
    ('SERVICE','Créditos','Citibanamex',                 0, 12, 1, 0, 'No. de tarjeta',          '#004990', 72),
    ('SERVICE','Créditos','Nu (Nubank)',                 0, 12, 1, 0, 'CLABE / tarjeta',         '#820ad1', 73),
    ('SERVICE','Créditos','Mercado Pago',                0, 10, 1, 0, 'Referencia',              '#00b1ea', 74),
    -- Streaming y gift cards
    ('SERVICE','Streaming','Netflix',                    0.03, 0, 50, 1000, 'Monto',             '#e50914', 80),
    ('SERVICE','Streaming','Spotify',                    0.03, 0, 50, 1000, 'Monto',             '#1db954', 81),
    ('SERVICE','Streaming','Disney+',                    0.03, 0, 50, 1000, 'Monto',             '#113ccf', 82),
    ('SERVICE','Streaming','HBO Max',                    0.03, 0, 50, 1000, 'Monto',             '#5b1fa8', 83),
    ('SERVICE','Streaming','Google Play',                0.03, 0, 50, 2000, 'Monto',             '#0f9d58', 84),
    ('SERVICE','Streaming','PlayStation',                0.03, 0, 50, 2000, 'Monto',             '#003791', 85),
    ('SERVICE','Streaming','Xbox',                       0.03, 0, 50, 2000, 'Monto',             '#107c10', 86),
    ('SERVICE','Streaming','Free Fire (Garena)',         0.04, 0, 20, 2000, 'ID de jugador',     '#f57f17', 87),
    -- Transporte y otros
    ('SERVICE','Otros','Tarjeta MI (Metro/Metrobús CDMX)', 0, 5,  1, 0, 'No. de tarjeta',        '#e5007d', 90),
    ('SERVICE','Otros','Peaje TAG (Televía)',            0, 8,  1, 0, 'No. de TAG',              '#0ea5e9', 91),
    ('SERVICE','Otros','Peaje IAVE (Capufe)',            0, 8,  1, 0, 'No. de TAG',              '#0f4c81', 92),
    ('SERVICE','Otros','Gasolineras (vale)',             0, 5,  1, 0, 'Referencia',              '#e30613', 93)
ON CONFLICT (op_type, provider) DO NOTHING;
