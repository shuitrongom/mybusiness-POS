package com.mybusinesssilva.bi.domain.model;

import java.math.BigDecimal;

/**
 * Sugerencia de compra para un producto, basada en existencia, mínimo y demanda reciente.
 *
 * @param productId       producto
 * @param name            nombre
 * @param currentStock    existencia actual
 * @param minQuantity     stock mínimo configurado
 * @param avgDailyDemand  demanda diaria promedio (histórico reciente)
 * @param suggestedQty    cantidad sugerida a comprar
 */
public record PurchaseSuggestion(
        long productId,
        String name,
        BigDecimal currentStock,
        BigDecimal minQuantity,
        BigDecimal avgDailyDemand,
        BigDecimal suggestedQty) {
}
