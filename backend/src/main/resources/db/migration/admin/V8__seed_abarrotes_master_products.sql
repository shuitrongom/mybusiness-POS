-- =====================================================================
-- Catálogo maestro del giro ABARROTES: productos frecuentes de una
-- tienda de abarrotes / tiendita de barrio en México.
--
-- source = 'SEED_ABARROTES'. Productos empaquetados por pieza (H87) y
-- graneles por kg/litro. Se incluyen marcas y códigos de barras comunes
-- donde aplica; el negocio ajusta el precio al adoptarlos.
--
-- Categorías: 'Bebidas', 'Botanas', 'Abarrotes', 'Enlatados', 'Lácteos',
-- 'Dulces y confitería', 'Higiene personal', 'Limpieza'.
--
-- SAT: bebidas 50200000; alimentos preparados/conservados 50190000;
-- lácteos/huevos 50130000; condimentos/conservantes 50170000;
-- higiene/limpieza usan 50000000 (genérico de la semilla). Idempotente por nombre.
-- =====================================================================

INSERT INTO admin.master_product (barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
SELECT v.barcode, v.name, v.brand, v.category, v.unit, v.sat_prod_serv, v.sat_unit, v.source
FROM (VALUES
    -- ---------- Bebidas ----------
    ('7501055363057', 'Coca-Cola 355 ml lata',    'Coca-Cola',   'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    ('7501055310517', 'Coca-Cola 2 L',            'Coca-Cola',   'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Coca-Cola Light 600 ml',   'Coca-Cola',   'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Sprite 600 ml',            'Sprite',      'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Fanta Naranja 600 ml',     'Fanta',       'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Fresca 600 ml',            'Fresca',      'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Pepsi 600 ml',             'Pepsi',       'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Manzanita Sol 600 ml',     'Manzanita',   'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Boing Mango 500 ml',       'Boing',       'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Jumex Durazno 335 ml',     'Jumex',       'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Agua Bonafont 1 L',        'Bonafont',    'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Agua Ciel 1.5 L',          'Ciel',        'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Powerade 500 ml',          'Powerade',    'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Gatorade 600 ml',          'Gatorade',    'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Red Bull 250 ml',          'Red Bull',    'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Café soluble Nescafé 50 g','Nescafé',     'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Cerveza Corona 355 ml',    'Corona',      'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Cerveza Tecate 355 ml',    'Tecate',      'Bebidas', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),

    -- ---------- Botanas ----------
    ('7501011128019', 'Sabritas Adobadas 45 g',   'Sabritas',    'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Doritos Nacho 62 g',       'Doritos',     'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Cheetos Torciditos 54 g',  'Cheetos',     'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Ruffles Queso 45 g',       'Ruffles',     'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Takis Fuego 62 g',         'Takis',       'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Cacahuates japoneses 60 g','Nishikawa',   'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Churrumais 65 g',          'Churrumais',  'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Palomitas Act II',         'Act II',      'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Galletas Emperador Chocolate','Gamesa',   'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Galletas Marías Gamesa',   'Gamesa',      'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Galletas Oreo',            'Oreo',        'Botanas', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),

    -- ---------- Abarrotes básicos ----------
    (NULL,            'Azúcar estándar 1 kg',     NULL,          'Abarrotes', 'pieza', '50170000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Sal de mesa 1 kg',         'La Fina',     'Abarrotes', 'pieza', '50170000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Arroz 1 kg',               'Verde Valle', 'Abarrotes', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Frijol negro 1 kg',        'Verde Valle', 'Abarrotes', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Aceite comestible 1 L',    '123',         'Abarrotes', 'pieza', '50170000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Harina de trigo 1 kg',     'Selecta',     'Abarrotes', 'pieza', '50170000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Harina de maíz Maseca 1 kg','Maseca',     'Abarrotes', 'pieza', '50170000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Pasta para sopa 200 g',    'La Moderna',  'Abarrotes', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Sopa instantánea Maruchan','Maruchan',    'Abarrotes', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Café de olla / molido 250 g',NULL,        'Abarrotes', 'pieza', '50200000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Consomé de pollo Knorr 1 kg','Knorr',     'Abarrotes', 'pieza', '50170000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Mayonesa McCormick 390 g', 'McCormick',   'Abarrotes', 'pieza', '50170000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Catsup Del Monte 370 g',   'Del Monte',   'Abarrotes', 'pieza', '50170000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Salsa Valentina 370 ml',   'Valentina',   'Abarrotes', 'pieza', '50170000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Azúcar (granel)',          NULL,          'Abarrotes', 'kg',    '50170000', 'KGM', 'SEED_ABARROTES'),
    (NULL,            'Frijol (granel)',          NULL,          'Abarrotes', 'kg',    '50000000', 'KGM', 'SEED_ABARROTES'),
    (NULL,            'Arroz (granel)',           NULL,          'Abarrotes', 'kg',    '50000000', 'KGM', 'SEED_ABARROTES'),

    -- ---------- Enlatados ----------
    (NULL,            'Atún en agua Dolores 140 g','Dolores',    'Enlatados', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Atún Tuny 140 g',          'Tuny',        'Enlatados', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Sardina en tomate La Costeña','La Costeña','Enlatados','pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Chiles jalapeños La Costeña 220 g','La Costeña','Enlatados','pieza','50190000','H87','SEED_ABARROTES'),
    (NULL,            'Frijoles refritos La Costeña','La Costeña','Enlatados','pieza',  '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Elote en grano Del Monte', 'Del Monte',   'Enlatados', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Leche condensada La Lechera','La Lechera', 'Enlatados','pieza',  '50130000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Chiles chipotles La Morena','La Morena',   'Enlatados','pieza',  '50190000', 'H87', 'SEED_ABARROTES'),

    -- ---------- Lácteos ----------
    ('7501020511573', 'Leche Lala Entera 1 L',    'Lala',        'Lácteos', 'pieza', '50130000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Leche Alpura Entera 1 L',  'Alpura',      'Lácteos', 'pieza', '50130000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Yogurt Danone 1 L',        'Danone',      'Lácteos', 'pieza', '50130000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Crema Lala 400 ml',        'Lala',        'Lácteos', 'pieza', '50130000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Queso panela 400 g',       NULL,          'Lácteos', 'pieza', '50130000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Queso Oaxaca (granel)',    NULL,          'Lácteos', 'kg',    '50130000', 'KGM', 'SEED_ABARROTES'),
    (NULL,            'Mantequilla 90 g',         NULL,          'Lácteos', 'pieza', '50130000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Huevo blanco 18 pzas',     'San Juan',    'Lácteos', 'paquete','50130000', 'XPK', 'SEED_ABARROTES'),
    (NULL,            'Huevo (granel)',           NULL,          'Lácteos', 'kg',    '50130000', 'KGM', 'SEED_ABARROTES'),

    -- ---------- Dulces y confitería ----------
    (NULL,            'Paleta Payaso',            'Ricolino',    'Dulces y confitería', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Pelón Pelo Rico',          'Lorena',      'Dulces y confitería', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Chicle Trident',           'Trident',     'Dulces y confitería', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Mazapán De la Rosa',       'De la Rosa',  'Dulces y confitería', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Chocolate Carlos V',       'Carlos V',    'Dulces y confitería', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Bubulubu',                 'Ricolino',    'Dulces y confitería', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Paleta Vero Mango',        'Vero',        'Dulces y confitería', 'pieza', '50190000', 'H87', 'SEED_ABARROTES'),

    -- ---------- Higiene personal ----------
    (NULL,            'Papel higiénico Regio 4 rollos','Regio',  'Higiene personal', 'paquete', '50000000', 'XPK', 'SEED_ABARROTES'),
    (NULL,            'Jabón de tocador Zote',    'Zote',        'Higiene personal', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Shampoo sachet',           NULL,          'Higiene personal', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Pasta dental Colgate 75 ml','Colgate',    'Higiene personal', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Toallas femeninas Saba',   'Saba',        'Higiene personal', 'paquete', '50000000', 'XPK', 'SEED_ABARROTES'),
    (NULL,            'Pañal Huggies etapa 3',    'Huggies',     'Higiene personal', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),

    -- ---------- Limpieza ----------
    (NULL,            'Detergente Roma 1 kg',     'Roma',        'Limpieza', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Jabón Zote para ropa',     'Zote',        'Limpieza', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Cloro Cloralex 950 ml',    'Cloralex',    'Limpieza', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Fabuloso 1 L',             'Fabuloso',    'Limpieza', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Suavitel 800 ml',          'Suavitel',    'Limpieza', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Fibra Scotch-Brite',       'Scotch-Brite','Limpieza', 'pieza', '50000000', 'H87', 'SEED_ABARROTES'),
    (NULL,            'Servilletas Pétalo',       'Pétalo',      'Limpieza', 'paquete', '50000000', 'XPK', 'SEED_ABARROTES')
) AS v(barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
WHERE NOT EXISTS (
    SELECT 1 FROM admin.master_product mp WHERE mp.name = v.name
);
