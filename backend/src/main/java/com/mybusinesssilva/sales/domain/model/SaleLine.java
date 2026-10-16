package com.mybusinesssilva.sales.domain.model;

import java.math.BigDecimal;

/**
 * Renglón de una venta.
 *
 * @param productId   producto vendido; puede ser {@code null} para un artículo
 *                    libre
 *                    (producto rápido / venta aislada) que no está en el
 *                    catálogo
 * @param description descripción (nombre del producto al momento de la venta);
 *                    obligatoria
 *                    cuando el renglón es un artículo libre (sin producto)
 * @param quantity    cantidad (soporta decimales para venta por peso)
 * @param unitPrice   precio unitario
 * @param discount    descuento aplicado al renglón
 * @param unit        unidad del renglón (pz, kg, ...); opcional, {@code null}
 *                    si no se envía.
 *                    El front ya la manda; relevante sobre todo para artículos
 *                    libres por peso.
 */
public record SaleLine(
        Long productId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discount,
        String unit) {

    public SaleLine {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva");
        }
        if (productId == null && (description == null || description.isBlank())) {
            throw new IllegalArgumentException("El artículo libre requiere descripción");
        }
        unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        discount = discount == null ? BigDecimal.ZERO : discount;
        unit = (unit == null || unit.isBlank()) ? null : unit;
    }

    /**
     * Constructor de conveniencia de 5 argumentos (sin unidad), compatible con los
     * call sites
     * existentes. Delega al canónico con {@code unit = null}.
     */
    public SaleLine(Long productId, String description, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal discount) {
        this(productId, description, quantity, unitPrice, discount, null);
    }

    /** @return importe del renglón: cantidad * precio unitario - descuento. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(quantity).subtract(discount);
    }
}
