package com.mybusinesssilva.bi.domain.model;

import java.math.BigDecimal;

/**
 * Renglón de ranking de productos (más vendidos, análisis ABC, rentabilidad).
 *
 * @param productId  producto
 * @param name       nombre
 * @param quantity   cantidad vendida
 * @param revenue    ingreso generado
 * @param profit     utilidad estimada (ingreso - costo)
 * @param abcClass   clase ABC (A, B, C) según acumulado de ingreso; nulo si no aplica
 */
public record ProductRanking(
        long productId,
        String name,
        BigDecimal quantity,
        BigDecimal revenue,
        BigDecimal profit,
        String abcClass) {
}
