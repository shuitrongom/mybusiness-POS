package com.mybusinesssilva.sales.domain.model;

import java.math.BigDecimal;

/**
 * Renglón de una venta.
 *
 * @param productId   producto vendido
 * @param description descripción (nombre del producto al momento de la venta)
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
        if (productId == null) {
            throw new IllegalArgumentException("El renglón requiere producto");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva");
        }
        unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        discount = discount == null ? BigDecimal.ZERO : discount;
    }

    /** @return importe del renglón: cantidad * precio unitario - descuento. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(quantity).subtract(discount);
    }
}
