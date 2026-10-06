-- =====================================================================
-- Catálogo maestro PROFUNDO del giro PANADERÍA (complemento de V6).
-- Variedades tradicionales y regionales de pan dulce, pan salado,
-- repostería, temporada y bizcochería mexicana. Productos a granel /
-- por pieza: NO llevan código de barras (el negocio los pesa o cuenta).
--
-- source = 'SEED_BAKERY'. SAT: productos de panadería 50180000, unidad
-- pieza H87 (los de venta por peso se marcan al sembrar al tenant).
-- Idempotente por nombre.
-- =====================================================================

INSERT INTO admin.master_product (barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
SELECT v.barcode, v.name, v.brand, v.category, v.unit, v.sat_prod_serv, v.sat_unit, v.source
FROM (VALUES
    -- ---------- Pan dulce (bizcochería) ----------
    (NULL, 'Concha de vainilla',        NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Concha de chocolate',       NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Concha de fresa',           NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cuernito',                  NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cuernito relleno de nata',  NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Oreja',                     NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Corbata',                   NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Garibaldi',                 NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Chilindrina',               NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Ojo de buey',               NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Ojo de pancha',             NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Bigote',                    NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Elote',                     NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Beso',                      NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rebanada (pan)',            NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Alamar',                    NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Ladrillo',                  NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Polvorón sevillano',        NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Polvorón tricolor',         NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cochito / marranito',       NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Puerquito de piloncillo',   NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Yoyo',                      NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Banderilla',               NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Empanada de piña',          NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Empanada de cajeta',        NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Empanada de crema',         NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cocol de anís',             NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Trenza',                    NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Trenza de nata',            NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Dona glaseada',             NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Dona de chocolate',         NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Dona espolvoreada',         NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Panqué de nuez (rebanada)', NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Panqué de naranja (rebanada)',NULL,'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Mantecada',                 NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Gendarme',                  NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Novia',                     NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Campechana',                NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Coricos',                   NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Semita',                    NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Chamuco',                   NULL, 'Pan dulce', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),

    -- ---------- Pan salado / bolillería ----------
    (NULL, 'Bolillo',                   NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Telera',                    NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Birote salado',             NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Baguette',                  NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan de caja blanco',        NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan de caja integral',      NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan para hot dog',          NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan para hamburguesa',      NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Chapata',                   NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cemita poblana',            NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan francés',               NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rol de canela',             NULL, 'Pan salado / bolillería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),

    -- ---------- Repostería ----------
    (NULL, 'Rebanada de pastel de tres leches', NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rebanada de pastel de chocolate',   NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rebanada de pay de queso',          NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rebanada de pay de limón',          NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cupcake',                           NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Brownie',                           NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Cheesecake individual',             NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Gelatina individual',               NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Flan individual',                   NULL, 'Repostería', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),

    -- ---------- Temporada ----------
    (NULL, 'Rosca de Reyes (chica)',    NULL, 'Temporada', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Rosca de Reyes (grande)',   NULL, 'Temporada', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan de muerto (chico)',     NULL, 'Temporada', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Pan de muerto (grande)',    NULL, 'Temporada', 'pieza', '50180000', 'H87', 'SEED_BAKERY'),
    (NULL, 'Buñuelo',                   NULL, 'Temporada', 'pieza', '50180000', 'H87', 'SEED_BAKERY')
) AS v(barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
WHERE NOT EXISTS (
    SELECT 1 FROM admin.master_product mp WHERE mp.name = v.name
);
