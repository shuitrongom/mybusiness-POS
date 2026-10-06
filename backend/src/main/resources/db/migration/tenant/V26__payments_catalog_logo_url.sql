-- =====================================================================
-- MyBusiness Silva — V26 (tenant): columna logo_url en el catálogo de pagos.
--
-- Prepara el sistema para logotipos OFICIALES autorizados: cuando el negocio
-- (o su agregador de recargas) provea los archivos de marca con derecho de uso,
-- se guarda aquí la URL/ruta y el frontend los muestra automáticamente. Mientras
-- tanto queda NULL y el frontend usa el arte propio / monograma.
--
-- Flyway sustituye ${tenant_schema}. Idempotente.
-- =====================================================================

ALTER TABLE ${tenant_schema}.payment_catalog
    ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500);
