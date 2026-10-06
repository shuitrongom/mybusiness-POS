-- =====================================================================
-- Amplía el catálogo de claves SAT (c_ClaveProdServ) con las clases
-- necesarias para clasificar los productos importados masivamente desde
-- Open Food Facts / Open Products Facts (higiene, limpieza, dulces,
-- cereales, cuidado del bebé, mascotas, etc.).
--
-- Se usan claves de CLASE (terminadas en 00), que el propio SAT admite
-- como agrupador válido cuando no se factura una clave de detalle exacta.
-- Todas las claves quedan referenciables por admin.master_product via FK.
-- Idempotente (ON CONFLICT DO NOTHING).
-- =====================================================================

INSERT INTO admin.sat_prod_serv (clave, descripcion) VALUES
    ('50192700', 'Dulces y chocolates'),
    ('50161500', 'Chocolate y azúcares'),
    ('50150000', 'Aceites y grasas comestibles'),
    ('50210000', 'Cereales'),
    ('50220000', 'Productos de molienda'),
    ('50161800', 'Botanas'),
    ('53131600', 'Productos de baño y cuidado del cuerpo'),
    ('53131500', 'Productos de cuidado personal'),
    ('47131800', 'Productos de limpieza'),
    ('47131600', 'Utensilios de limpieza'),
    ('14111700', 'Papel higiénico y facial'),
    ('42280000', 'Productos para el cuidado del bebé'),
    ('10121800', 'Alimentos y golosinas para mascotas'),
    ('50193000', 'Alimentos preparados diversos'),
    ('50171500', 'Condimentos y aderezos'),
    ('50202300', 'Café, té e infusiones')
ON CONFLICT (clave) DO NOTHING;
