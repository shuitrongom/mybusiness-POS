-- =====================================================================
-- Catálogo maestro del giro POLLERÍA: productos frecuentes de una
-- pollería mexicana (cortes de pollo, menudencias, procesados y derivados).
--
-- Se cargan como productos maestros compartidos (source = 'SEED_POLLERIA').
-- La mayoría se vende POR PESO (unidad Kilogramo / KGM); las piezas contadas
-- usan Pieza (H87). SAT: 'Carnes y aves' (50100000); huevo usa lácteos/huevos
-- (50130000). Un negocio de pollería los adopta y solo ajusta el precio.
--
-- Categorías dentro de Pollería:
--   'Pollo en corte', 'Menudencias', 'Pollo procesado', 'Otras aves y derivados'.
--
-- Idempotente: solo inserta si no existe ya un producto maestro con ese nombre.
-- =====================================================================

INSERT INTO admin.master_product (barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
SELECT v.barcode, v.name, v.brand, v.category, v.unit, v.sat_prod_serv, v.sat_unit, v.source
FROM (VALUES
    -- ---------- Pollo en corte (por kg) ----------
    (NULL, 'Pollo entero',                 NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pollo entero en piezas',       NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pechuga con hueso',            NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pechuga sin hueso',            NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Filete de pechuga',            NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Media pechuga',                NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pierna con muslo',             NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pierna',                       NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Muslo',                        NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Contramuslo',                  NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Alas de pollo',                NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Alitas tipo colita',           NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Rabadilla',                    NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Retazo con hueso',             NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Retazo sin hueso',             NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Espinazo de pollo',            NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),

    -- ---------- Menudencias (por kg) ----------
    (NULL, 'Hígado de pollo',              NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Molleja de pollo',             NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Corazón de pollo',             NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Patas de pollo',               NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pescuezo de pollo',            NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Menudencias surtidas',         NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),

    -- ---------- Pollo procesado (por kg) ----------
    (NULL, 'Milanesa de pollo',            NULL, 'Pollo procesado', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Fajitas de pollo',             NULL, 'Pollo procesado', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pollo molido',                 NULL, 'Pollo procesado', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Nuggets de pollo',             NULL, 'Pollo procesado', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pollo marinado (adobo)',       NULL, 'Pollo procesado', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pollo para asar marinado',     NULL, 'Pollo procesado', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Brochetas de pollo',           NULL, 'Pollo procesado', 'pieza', '50100000', 'H87', 'SEED_POLLERIA'),

    -- ---------- Otras aves y derivados ----------
    (NULL, 'Pechuga de pavo',              NULL, 'Otras aves y derivados', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pavo entero',                  NULL, 'Otras aves y derivados', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Huevo blanco por kg',          NULL, 'Otras aves y derivados', 'kg', '50130000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Huevo rojo por kg',            NULL, 'Otras aves y derivados', 'kg', '50130000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Cartón de huevo (30 pzas)',    NULL, 'Otras aves y derivados', 'paquete', '50130000', 'XPK', 'SEED_POLLERIA'),
    (NULL, 'Chorizo de pollo',             NULL, 'Otras aves y derivados', 'kg', '50100000', 'KGM', 'SEED_POLLERIA')
) AS v(barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
WHERE NOT EXISTS (
    SELECT 1 FROM admin.master_product mp WHERE mp.name = v.name
);
