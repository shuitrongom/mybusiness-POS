package com.mybusinesssilva.catalog.domain.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Producto del catálogo de un negocio.
 *
 * <p>Los campos comunes están tipados; los específicos del giro (caducidad, lote, calibre, etc.)
 * viven en {@code attributes}, cuya forma la define el perfil de giro del negocio. Los campos
 * profesionales adicionales (multiprecio, niveles de inventario, banderas, impuesto, proveedor)
 * se agrupan en {@link ProductExtras} para mantener el record principal manejable y no romper el
 * código existente que crea productos básicos.
 *
 * @param id            identificador (nulo hasta persistir)
 * @param sku           código interno del negocio (opcional)
 * @param name          nombre del producto
 * @param categoryId    categoría (opcional)
 * @param unit          unidad de venta (pieza, kg, litro...)
 * @param soldByWeight  true si se vende por peso (báscula)
 * @param satProdServ   clave SAT de producto/servicio (para CFDI)
 * @param satUnit       clave SAT de unidad (para CFDI)
 * @param price         precio de venta (lista / precio 1)
 * @param cost          costo
 * @param active        si está activo
 * @param barcodes      códigos de barras asociados
 * @param attributes    campos dinámicos del giro
 * @param imageUrl      foto del producto (URL o data URL base64); puede ser nula
 * @param extras        campos profesionales (multiprecio, inventario, banderas); nunca nulo
 */
public record Product(
        Long id,
        String sku,
        String name,
        Long categoryId,
        String unit,
        boolean soldByWeight,
        String satProdServ,
        String satUnit,
        BigDecimal price,
        BigDecimal cost,
        boolean active,
        java.util.List<String> barcodes,
        Map<String, Object> attributes,
        String imageUrl,
        ProductExtras extras) {

    public Product {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }
        unit = (unit == null || unit.isBlank()) ? "pieza" : unit;
        price = price == null ? BigDecimal.ZERO : price;
        cost = cost == null ? BigDecimal.ZERO : cost;
        barcodes = barcodes == null ? java.util.List.of() : java.util.List.copyOf(barcodes);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        extras = extras == null ? ProductExtras.defaults() : extras;
    }

    /**
     * Campos profesionales del producto (nivel enterprise). Todos con valores por defecto para
     * que un producto simple no tenga que especificarlos.
     *
     * @param description    descripción larga
     * @param brand          marca
     * @param supplierId     proveedor principal (opcional)
     * @param taxRate        tasa de IVA en fracción (0.16 = 16%)
     * @param iepsRate       tasa de IEPS en fracción (0 si no aplica)
     * @param price2         precio 2 (mayoreo)
     * @param price3         precio 3 (medio mayoreo)
     * @param price4         precio 4 (especial)
     * @param price5         precio 5 (distribuidor)
     * @param lastCost       costo de la última compra
     * @param avgCost        costo promedio
     * @param minStock       existencia mínima (punto de reorden bajo)
     * @param maxStock       existencia máxima (nivel objetivo)
     * @param reorderPoint   punto de reorden
     * @param forSale        se puede vender
     * @param trackInventory controla existencias
     * @param trackLots      controla lotes/caducidad
     * @param allowBelowCost permite vender por debajo del costo
     * @param blocked        bloqueado (no operable)
     * @param isComposite    artículo compuesto (kit)
     * @param onSale         en oferta
     * @param loyaltyPoints  puntos de lealtad que otorga
     */
    public record ProductExtras(
            String description,
            String brand,
            Long supplierId,
            BigDecimal taxRate,
            BigDecimal iepsRate,
            BigDecimal price2,
            BigDecimal price3,
            BigDecimal price4,
            BigDecimal price5,
            BigDecimal lastCost,
            BigDecimal avgCost,
            BigDecimal minStock,
            BigDecimal maxStock,
            BigDecimal reorderPoint,
            boolean forSale,
            boolean trackInventory,
            boolean trackLots,
            boolean allowBelowCost,
            boolean blocked,
            boolean isComposite,
            boolean onSale,
            BigDecimal loyaltyPoints) {

        public ProductExtras {
            taxRate = taxRate == null ? new BigDecimal("0.16") : taxRate;
            iepsRate = zeroIfNull(iepsRate);
            price2 = zeroIfNull(price2);
            price3 = zeroIfNull(price3);
            price4 = zeroIfNull(price4);
            price5 = zeroIfNull(price5);
            lastCost = zeroIfNull(lastCost);
            avgCost = zeroIfNull(avgCost);
            minStock = zeroIfNull(minStock);
            maxStock = zeroIfNull(maxStock);
            reorderPoint = zeroIfNull(reorderPoint);
            loyaltyPoints = zeroIfNull(loyaltyPoints);
        }

        private static BigDecimal zeroIfNull(BigDecimal v) {
            return v == null ? BigDecimal.ZERO : v;
        }

        /** Valores por defecto de un producto simple (vendible, con control de inventario, IVA 16%). */
        public static ProductExtras defaults() {
            return new ProductExtras(
                    null, null, null, new BigDecimal("0.16"), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    true, true, false, false, false, false, false, BigDecimal.ZERO);
        }
    }

    /** Constructor de conveniencia sin extras (usa los valores por defecto). */
    public Product(Long id, String sku, String name, Long categoryId, String unit,
                   boolean soldByWeight, String satProdServ, String satUnit,
                   BigDecimal price, BigDecimal cost, boolean active,
                   java.util.List<String> barcodes, Map<String, Object> attributes,
                   String imageUrl) {
        this(id, sku, name, categoryId, unit, soldByWeight, satProdServ, satUnit,
                price, cost, active, barcodes, attributes, imageUrl, ProductExtras.defaults());
    }

    /** Constructor de conveniencia sin imagen ni extras. */
    public Product(Long id, String sku, String name, Long categoryId, String unit,
                   boolean soldByWeight, String satProdServ, String satUnit,
                   BigDecimal price, BigDecimal cost, boolean active,
                   java.util.List<String> barcodes, Map<String, Object> attributes) {
        this(id, sku, name, categoryId, unit, soldByWeight, satProdServ, satUnit,
                price, cost, active, barcodes, attributes, null, ProductExtras.defaults());
    }
}
