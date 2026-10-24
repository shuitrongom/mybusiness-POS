-- =====================================================================
-- V45 (tenant): limpieza de conceptos de salida de panadería.
--   1) Desactiva los conceptos OUT de MERMA del dropdown de "Salida normal"
--      (Hueco 3): duplican los flujos dedicados. Se marcan active=FALSE
--      (NO se borran) para conservar los documentos históricos que los usaron.
--   2) Siembra el concepto OUT "Reparto a repartidor" (Hueco 4), NO-merma,
--      usado por la pata de pan del día de un reparto mixto.
-- Aditiva/idempotente. No toca cantidades ni saldos. No afecta V42/V43/V44
-- ni el cuadre de inventario/merma.
--
-- Número de versión: V45. La más alta tenant aplicada es V44 (folio M-),
--   confirmado por lectura del directorio db/migration/tenant. No existe V45.
--   La migración admin V15 es de OTRO workflow (buzón de soporte) y NO se toca
--   aquí (numeración admin independiente de la numeración tenant).
--
-- Idempotente: (1) UPDATE por nombre exacto (reaplicar no cambia nada);
--   (2) INSERT ... SELECT ... WHERE NOT EXISTS (no duplica el concepto).
-- El DDL/DML no está sujeto a RLS; se fija app.current_tenant por consistencia
-- con V41/V42/V43/V44. Flyway sustituye ${tenant_schema}. NO ejecutar a mano:
-- Flyway corre al desplegar.
-- =====================================================================

SET app.current_tenant = '${tenant_schema}';

-- 1) Quitar del dropdown de salida normal los conceptos de merma.
UPDATE ${tenant_schema}.inventory_concept
   SET active = FALSE
 WHERE direction = 'OUT'
   AND name IN ('Merma: se dio a repartidor', 'Salida por merma');

-- 2) Concepto de reparto de pan del día (no-merma), idempotente por nombre.
INSERT INTO ${tenant_schema}.inventory_concept (name, direction, active)
SELECT 'Reparto a repartidor', 'OUT', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM ${tenant_schema}.inventory_concept
     WHERE name = 'Reparto a repartidor' AND direction = 'OUT');
