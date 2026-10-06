-- =====================================================================
-- Catálogo maestro del giro PANADERÍA: panes mexicanos frecuentes.
-- Se cargan como productos maestros compartidos (source = 'SEED_BAKERY'),
-- que un negocio de panadería puede adoptar y solo ajustarles el precio.
--
-- Todos son "Productos de panadería" (SAT 50180000), unidad Pieza (H87),
-- salvo los que se venden por peso a granel (Kilogramo / KGM).
--
-- Categorías usadas dentro de Panadería:
--   'Pan dulce', 'Pan salado / bolillería', 'Repostería', 'Temporada'.
--
-- Nombres tomados del anexo de panes de México (Wikipedia). Idempotente:
-- solo inserta un pan si no existe ya un producto maestro con ese nombre.
-- =====================================================================

INSERT INTO admin.master_product (barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
SELECT v.barcode, v.name, v.brand, v.category, v.unit, v.sat_prod_serv, v.sat_unit, v.source
FROM (VALUES
    -- ---------- Pan salado / bolillería ----------
    (NULL, 'Bolillo',                 NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Telera',                  NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Birote',                  NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Baguette',                NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan de caja blanco',      NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cemita',                  NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pambazo',                 NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Bisquet',                 NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cuernito salado',         NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Chapata (ciabatta)',      NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Mollete',                 NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan de sal',              NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),

    -- ---------- Pan dulce ----------
    (NULL, 'Concha de vainilla',      NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Concha de chocolate',     NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Concha de fresa',         NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Manteconcha',             NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cuernito dulce',          NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Oreja',                   NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Chilindrina',            NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cocol',                   NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cocotazo',                NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Campechana',             NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Banderilla',             NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Bigote',                  NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Corbata (moño)',          NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cortadillo',              NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Caracol',                 NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cubilete',                NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cachito de canela',       NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Chamuco (garibaldi negro)', NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Garibaldi',              NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Dona glaseada',           NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Dona de chocolate',       NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Elote (pan dulce)',       NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Empanada de piña',        NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Empanada de cajeta',      NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Espejo',                  NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Gusano de canela',        NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Hojaldra dulce',          NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Hojarasca',               NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Ladrillo',                NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Mantecada',               NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Marranito (puerquito)',   NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Muégano',                 NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Novia',                   NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Nido (danés)',            NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Ojo de buey',             NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Ojo de Pancha',           NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Ombligo',                 NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan de yema',             NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan de pulque',           NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan de huevo',            NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Panqué de vainilla (rebanada)', NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Panqué de nuez (rebanada)', NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pechuga',                 NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Peineta',                 NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Picón',                   NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Piedra (terrón)',         NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Polvorón de naranja',     NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Polvorón sevillano',      NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Puro de canela',          NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rebanada de mantequilla', NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rehilete',                NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Riel de manzana',         NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rosca de canela',         NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Trenza',                  NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Volcán',                  NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Yoyo (beso)',             NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),

    -- ---------- Repostería ----------
    (NULL, 'Churro',                  NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Buñuelo',                 NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cuerno relleno de crema', NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cono de crema',           NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Gordita de nata',         NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Niño envuelto',           NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan de elote (rebanada)', NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),

    -- ---------- Temporada ----------
    (NULL, 'Pan de muerto',           NULL, 'Temporada', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rosca de Reyes chica',    NULL, 'Temporada', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rosca de Reyes grande',   NULL, 'Temporada', 'pieza', '50180000', 'H87', 'SEED_BAKERY')
) AS v(barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
WHERE NOT EXISTS (
    SELECT 1 FROM admin.master_product mp WHERE mp.name = v.name
);
