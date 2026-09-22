-- =====================================================================
-- MyBusiness Silva — V11 (tenant): foto del producto.
--
-- Agrega image_url a product para mostrar la imagen del producto en el
-- punto de venta y el catálogo. Puede contener una URL o una imagen
-- embebida como data URL (base64), según cómo la capture el negocio.
--
-- Flyway sustituye ${tenant_schema} por el schema destino.
-- =====================================================================

ALTER TABLE ${tenant_schema}.product
    ADD COLUMN IF NOT EXISTS image_url TEXT;
