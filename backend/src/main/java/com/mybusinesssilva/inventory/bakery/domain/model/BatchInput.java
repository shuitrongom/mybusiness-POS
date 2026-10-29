package com.mybusinesssilva.inventory.bakery.domain.model;

import java.math.BigDecimal;

/**
 * Línea de insumo consumido en una tanda. {@code unitCost}/{@code lineCost} se
 * congelan desde {@code raw_material.unit_cost} al momento del consumo.
 *
 * @param rawMaterialId insumo consumido
 * @param quantity      cantidad consumida (positiva)
 * @param unit          unidad (copiada del insumo, histórico)
 * @param unitCost      costo unitario congelado
 * @param lineCost      quantity·unitCost
 */
public record BatchInput(
        long rawMaterialId,
        BigDecimal quantity,
        String unit,
        BigDecimal unitCost,
        BigDecimal lineCost) {
}
