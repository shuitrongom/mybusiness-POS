package com.mybusinesssilva.sales.domain.model;

import java.math.BigDecimal;

/**
 * Renglón de una venta.
 *
 * @param productId   producto vendido; puede ser {@code null} para un artículo libre
 *                    (producto rápido / venta aislada) que no está en el catálogo
 * @param description descripción (nombre del producto al momento de la venta); obligatoria
 *                    cuando el renglón es un artículo libre (sin producto)
 * @param quantity    cantidad (soporta decimales para venta por peso)
 * @param unitPrice   precio unitario
 * @param discount    descuento aplicado al renglón
 */
public record SaleLine(
        Long productId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discount) {

    public SaleLine {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva");
        }
        if (productId == null && (description == null || description.isBlank())) {
            throw new IllegalArgumentException("El artículo libre requiere descripción");
        }
        unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        discount = discount == null ? BigDecimal.ZERO : discount;
    }

    /** @return importe del renglón: cantidad * precio unitario - descuento. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(quantity).subtract(discount);
    }
}
