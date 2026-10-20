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
 * @param waste       {@code true} si el renglón es una VENTA DE MERMA de
 *                    panadería: se cobra al
 *                    precio de merma fijo del artículo y descuenta de la
 *                    existencia de merma, no
 *                    del stock normal. {@code false} para una venta normal.
 */
public record SaleLine(
        Long productId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discount,
        String unit,
        boolean waste) {

    public SaleLine {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva");
        }
        if (productId == null && (description == null || description.isBlank())) {
            throw new IllegalArgumentException("El artículo libre requiere descripción");
        }
        if (waste && productId == null) {
            throw new IllegalArgumentException("La venta de merma requiere un producto del catálogo");
        }
        unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        discount = discount == null ? BigDecimal.ZERO : discount;
        unit = (unit == null || unit.isBlank()) ? null : unit;
        // Invariante de no-negatividad del renglón (red de seguridad; el servicio ya
        // acota el descuento). El descuento no puede volver negativo el importe del
        // renglón. Aplica a TODOS los call sites (constructores de 5/6/7 args),
        // incluido
        // registerQuote y el flujo de void: una cotización con descuento > importe se
        // rechaza con 422 (GlobalExceptionHandler mapea IllegalArgumentException).
        if (unitPrice.multiply(quantity).subtract(discount).signum() < 0) {
            throw new IllegalArgumentException(
                    "El descuento del renglón no puede superar su importe");
        }
    }

    /**
     * Constructor de conveniencia de 6 argumentos (sin marca de merma), compatible
     * con los
     * call sites existentes. Delega al canónico con {@code waste = false}.
     */
    public SaleLine(Long productId, String description, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal discount, String unit) {
        this(productId, description, quantity, unitPrice, discount, unit, false);
    }

    /**
     * Constructor de conveniencia de 5 argumentos (sin unidad ni merma), compatible
     * con los
     * call sites existentes. Delega al canónico con
     * {@code unit = null, waste = false}.
     */
    public SaleLine(Long productId, String description, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal discount) {
        this(productId, description, quantity, unitPrice, discount, null, false);
    }

    /** @return importe del renglón: cantidad * precio unitario - descuento. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(quantity).subtract(discount);
    }
}
