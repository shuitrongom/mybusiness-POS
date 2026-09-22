package com.mybusinesssilva.catalog.domain.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Producto del catálogo de un negocio.
 *
 * <p>Los campos comunes están tipados; los específicos del giro (caducidad, lote, calibre, etc.)
 * viven en {@code attributes}, cuya forma la define el perfil de giro del negocio.
 *
 * @param id            identificador (nulo hasta persistir)
 * @param sku           código interno del negocio (opcional)
 * @param name          nombre del producto
 * @param categoryId    categoría (opcional)
 * @param unit          unidad de venta (pieza, kg, litro...)
 * @param soldByWeight  true si se vende por peso (báscula)
 * @param satProdServ   clave SAT de producto/servicio (para CFDI)
 * @param satUnit       clave SAT de unidad (para CFDI)
 * @param price         precio de venta
 * @param cost          costo
 * @param active        si está activo
 * @param barcodes      códigos de barras asociados
 * @param attributes    campos dinámicos del giro
 * @param imageUrl      foto del producto (URL o data URL base64); puede ser nula
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
        String imageUrl) {

    public Product {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }
        unit = (unit == null || unit.isBlank()) ? "pieza" : unit;
        price = price == null ? BigDecimal.ZERO : price;
        cost = cost == null ? BigDecimal.ZERO : cost;
        barcodes = barcodes == null ? java.util.List.of() : java.util.List.copyOf(barcodes);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * Constructor de conveniencia sin imagen (imageUrl = null). Mantiene compatibilidad con el
     * código que crea productos sin foto.
     */
    public Product(Long id, String sku, String name, Long categoryId, String unit,
                   boolean soldByWeight, String satProdServ, String satUnit,
                   BigDecimal price, BigDecimal cost, boolean active,
                   java.util.List<String> barcodes, Map<String, Object> attributes) {
        this(id, sku, name, categoryId, unit, soldByWeight, satProdServ, satUnit,
                price, cost, active, barcodes, attributes, null);
    }
}
