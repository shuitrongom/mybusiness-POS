-- =====================================================================
-- El nombre de schema de un negocio (schema_name) se deriva de su id, que
-- se genera al insertar. Por eso se asigna en un segundo paso, dentro de la
-- misma transacción de alta. Se permite temporalmente NULL para soportar ese
-- flujo insert->update; la aplicación garantiza que nunca queda nulo tras el alta.
-- =====================================================================
ALTER TABLE admin.business ALTER COLUMN schema_name DROP NOT NULL;
