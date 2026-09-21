-- =====================================================================
-- Semilla de perfiles de giro, sus campos dinámicos, claves SAT de uso común
-- y catálogo maestro curado de productos frecuentes en México.
-- =====================================================================

-- ---- Claves SAT de unidad de uso común ----
INSERT INTO admin.sat_unit (clave, nombre) VALUES
    ('H87', 'Pieza'),
    ('KGM', 'Kilogramo'),
    ('GRM', 'Gramo'),
    ('LTR', 'Litro'),
    ('MLT', 'Mililitro'),
    ('XPK', 'Paquete'),
    ('XBX', 'Caja'),
    ('E48', 'Servicio')
ON CONFLICT (clave) DO NOTHING;

-- ---- Claves SAT de producto/servicio de uso común (subconjunto) ----
-- El catálogo completo (50k claves) se importa por proceso de carga; aquí van las frecuentes.
INSERT INTO admin.sat_prod_serv (clave, descripcion) VALUES
    ('50000000', 'Alimentos, bebidas y tabaco'),
    ('50190000', 'Alimentos preparados y conservados'),
    ('50200000', 'Bebidas'),
    ('50130000', 'Productos lácteos y huevos'),
    ('50180000', 'Productos de panadería'),
    ('50100000', 'Carnes y aves'),
    ('50170000', 'Condimentos y conservantes'),
    ('01010101', 'No existe en el catálogo')
ON CONFLICT (clave) DO NOTHING;

-- ---- Perfiles de giro ----
INSERT INTO admin.business_line (code, name, description) VALUES
    ('abarrotes',      'Abarrotes',       'Tienda de abarrotes y conveniencia.'),
    ('materias_primas','Materias primas', 'Venta de materias primas e insumos a granel.'),
    ('panaderia',      'Panadería',       'Elaboración y venta de pan y repostería.'),
    ('polleria',       'Pollería',        'Venta de pollo y derivados.')
ON CONFLICT (code) DO NOTHING;

-- ---- Campos dinámicos por giro ----
-- Abarrotes: control de caducidad y lote.
INSERT INTO admin.business_line_field (line_code, field_key, label, data_type, required, display_order) VALUES
    ('abarrotes', 'expiration_date', 'Fecha de caducidad', 'DATE',   false, 1),
    ('abarrotes', 'lot',             'Lote',               'STRING', false, 2),
    ('abarrotes', 'sold_by_weight',  'Se vende por peso',  'BOOLEAN',false, 3)
ON CONFLICT (line_code, field_key) DO NOTHING;

-- Materias primas: venta a granel por peso.
INSERT INTO admin.business_line_field (line_code, field_key, label, data_type, required, display_order) VALUES
    ('materias_primas', 'sold_by_weight', 'Se vende por peso', 'BOOLEAN', true, 1),
    ('materias_primas', 'unit_weight_kg', 'Peso por unidad (kg)', 'NUMBER', false, 2),
    ('materias_primas', 'lot',            'Lote',              'STRING', false, 3)
ON CONFLICT (line_code, field_key) DO NOTHING;

-- Panadería: caducidad corta y tipo de producto.
INSERT INTO admin.business_line_field (line_code, field_key, label, data_type, required, display_order) VALUES
    ('panaderia', 'expiration_date', 'Fecha de caducidad', 'DATE',   false, 1),
    ('panaderia', 'baked_at',        'Hora de horneado',   'STRING', false, 2),
    ('panaderia', 'sold_by_weight',  'Se vende por peso',  'BOOLEAN',false, 3)
ON CONFLICT (line_code, field_key) DO NOTHING;

-- Pollería: peso y fecha de sacrificio/empaque.
INSERT INTO admin.business_line_field (line_code, field_key, label, data_type, required, display_order) VALUES
    ('polleria', 'sold_by_weight', 'Se vende por peso',  'BOOLEAN', true, 1),
    ('polleria', 'packed_date',    'Fecha de empaque',   'DATE',    false, 2),
    ('polleria', 'cut_type',       'Tipo de corte',      'STRING',  false, 3)
ON CONFLICT (line_code, field_key) DO NOTHING;

-- ---- Módulos sugeridos por giro ----
-- Todos los giros sugieren el núcleo operativo; el Super Admin lo ajusta al vender.
INSERT INTO admin.business_line_module (line_code, module_key)
SELECT bl.code, m.module_key
FROM admin.business_line bl
CROSS JOIN (VALUES ('sales'),('inventory'),('printing'),('customers'),('cash')) AS m(module_key)
ON CONFLICT DO NOTHING;

-- ---- Catálogo maestro curado (productos frecuentes en México) ----
INSERT INTO admin.master_product (barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source) VALUES
    ('7501055300201', 'Coca-Cola 600 ml',            'Coca-Cola', 'Bebidas',   'pieza', '50200000', 'H87', 'SEED'),
    ('7501000111095', 'Sabritas Original 45 g',      'Sabritas',  'Botanas',   'pieza', '50190000', 'H87', 'SEED'),
    ('7501000634736', 'Pan Blanco Bimbo Grande',     'Bimbo',     'Panadería', 'pieza', '50180000', 'H87', 'SEED'),
    ('7501008042632', 'Leche Lala Entera 1 L',       'Lala',      'Lácteos',   'pieza', '50130000', 'H87', 'SEED'),
    ('7501030459415', 'Huevo San Juan 18 pzas',      'San Juan',  'Lácteos',   'paquete','50130000','XPK', 'SEED'),
    (NULL,            'Azúcar estándar (granel)',    NULL,        'Abarrotes', 'kg',    '50170000', 'KGM', 'SEED'),
    (NULL,            'Harina de trigo (granel)',    NULL,        'Materias primas','kg','50170000','KGM','SEED'),
    (NULL,            'Frijol negro (granel)',       NULL,        'Materias primas','kg','50000000','KGM','SEED'),
    (NULL,            'Arroz (granel)',              NULL,        'Materias primas','kg','50000000','KGM','SEED'),
    (NULL,            'Aceite comestible (granel)',  NULL,        'Abarrotes', 'litro', '50170000', 'LTR', 'SEED'),
    (NULL,            'Pollo entero',                NULL,        'Pollería',  'kg',    '50100000', 'KGM', 'SEED'),
    (NULL,            'Pechuga de pollo',            NULL,        'Pollería',  'kg',    '50100000', 'KGM', 'SEED'),
    (NULL,            'Bolillo',                     NULL,        'Panadería', 'pieza', '50180000', 'H87', 'SEED'),
    (NULL,            'Concha',                      NULL,        'Panadería', 'pieza', '50180000', 'H87', 'SEED')
ON CONFLICT (barcode) DO NOTHING;
