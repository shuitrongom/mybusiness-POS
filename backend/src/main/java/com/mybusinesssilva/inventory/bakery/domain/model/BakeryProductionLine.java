package com.mybusinesssilva.inventory.bakery.domain.model;

import java.math.BigDecimal;

/**
 * Renglón de una entrada de producción de pan.
 *
 * @param id          identificador (null antes de persistir)
 * @param productId   producto de pan
 * @param description descripción del renglón
 * @param quantity    cantidad producida
 * @param unit        unidad (pz, kg, …)
 * @param entryPrice  precio de entrada (base para la merma)
 * @param unitCost    costo unitario
 * @param lineTotal   importe del renglón
 */
public record BakeryProductionLine(
        Long id,
        long productId,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal entryPrice,
        BigDecimal unitCost,
        BigDecimal lineTotal) {
}
