package com.mybusinesssilva.inventory.bakery.domain.model;

import java.math.BigDecimal;

/**
 * Línea de pan producido en una tanda (espejo para rendimiento). {@code entryPrice}
 * se congela desde la ficha del producto (no se deriva del costo de MP).
 *
 * @param productId  pan producido
 * @param quantity   piezas producidas (positiva)
 * @param unit       unidad
 * @param entryPrice precio de ficha congelado
 * @param lineValue  quantity·entryPrice
 */
public record BatchOutput(
        long productId,
        BigDecimal quantity,
        String unit,
        BigDecimal entryPrice,
        BigDecimal lineValue) {
}
