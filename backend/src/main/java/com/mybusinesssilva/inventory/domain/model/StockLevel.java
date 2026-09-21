package com.mybusinesssilva.inventory.domain.model;

import java.math.BigDecimal;

/**
 * Existencia de un producto en una sucursal.
 *
 * @param productId   producto
 * @param branchId    sucursal
 * @param quantity    existencia actual
 * @param minQuantity stock mínimo (umbral de alerta)
 */
public record StockLevel(
        Long productId,
        Long branchId,
        BigDecimal quantity,
        BigDecimal minQuantity) {

    /** @return true si la existencia está en o por debajo del mínimo (requiere reabastecer). */
    public boolean isBelowMinimum() {
        return minQuantity != null
                && minQuantity.signum() > 0
                && quantity.compareTo(minQuantity) <= 0;
    }
}
