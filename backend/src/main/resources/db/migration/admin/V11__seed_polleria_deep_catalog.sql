-- =====================================================================
-- Catálogo maestro PROFUNDO del giro POLLERÍA (complemento de V7).
-- Cortes de pollo, menudencias, productos procesados y otras aves.
-- La mayoría se vende POR PESO (kg): unidad 'kg' y SAT unidad KGM; el
-- seeder del tenant los marca como sold_by_weight automáticamente.
--
-- source = 'SEED_POLLERIA'. SAT producto: carnes y aves 50100000.
-- Idempotente por nombre.
-- =====================================================================

INSERT INTO admin.master_product (barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
SELECT v.barcode, v.name, v.brand, v.category, v.unit, v.sat_prod_serv, v.sat_unit, v.source
FROM (VALUES
    -- ---------- Pollo en corte (por peso) ----------
    (NULL, 'Pollo entero limpio',        NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pollo entero con menudencias',NULL,'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Medio pollo',                NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pechuga con hueso',          NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pechuga sin hueso',          NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Filete de pechuga',          NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Milanesa de pollo',          NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pierna con muslo',           NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pierna sola',                NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Muslo',                      NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Ala de pollo',               NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Alita (bandera)',            NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Rabadilla',                  NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Retazo con hueso',           NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pollo en trozo para caldo',  NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Fajitas de pollo',           NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Molida de pollo',            NULL, 'Pollo en corte', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),

    -- ---------- Menudencias (por peso) ----------
    (NULL, 'Hígado de pollo',            NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Molleja',                    NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Corazón de pollo',           NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Patas de pollo',             NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pescuezo de pollo',          NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Cuero de pollo',             NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Huacal (esqueleto)',         NULL, 'Menudencias', 'kg', '50100000', 'KGM', 'SEED_POLLERIA'),

    -- ---------- Pollo procesado (por pieza/paquete) ----------
    (NULL, 'Pollo rostizado entero',     NULL, 'Pollo procesado', 'pieza', '50100000', 'H87', 'SEED_POLLERIA'),
    (NULL, 'Medio pollo rostizado',      NULL, 'Pollo procesado', 'pieza', '50100000', 'H87', 'SEED_POLLERIA'),
    (NULL, 'Pollo adobado',              NULL, 'Pollo procesado', 'kg',    '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pechuga marinada',           NULL, 'Pollo procesado', 'kg',    '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Nuggets de pollo (granel)',  NULL, 'Pollo procesado', 'kg',    '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Salchicha de pollo',         NULL, 'Pollo procesado', 'kg',    '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Chorizo de pollo',           NULL, 'Pollo procesado', 'kg',    '50100000', 'KGM', 'SEED_POLLERIA'),

    -- ---------- Otras aves y derivados ----------
    (NULL, 'Huevo blanco (granel)',      NULL, 'Otras aves y derivados', 'kg',    '50130000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Huevo rojo (granel)',        NULL, 'Otras aves y derivados', 'kg',    '50130000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pechuga de pavo',            NULL, 'Otras aves y derivados', 'kg',    '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Pavo entero',                NULL, 'Otras aves y derivados', 'kg',    '50100000', 'KGM', 'SEED_POLLERIA'),
    (NULL, 'Codorniz',                   NULL, 'Otras aves y derivados', 'pieza', '50100000', 'H87', 'SEED_POLLERIA'),
    (NULL, 'Pato',                       NULL, 'Otras aves y derivados', 'kg',    '50100000', 'KGM', 'SEED_POLLERIA')
) AS v(barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
WHERE NOT EXISTS (
    SELECT 1 FROM admin.master_product mp WHERE mp.name = v.name
);
