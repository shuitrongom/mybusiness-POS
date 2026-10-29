package com.mybusinesssilva.inventory.bakery.domain.model;

import java.math.BigDecimal;

/**
 * Veredicto de rendimiento de una tanda: el rendimiento total (panes/insumo), el
 * promedio móvil de referencia y el desvío contra ese promedio.
 *
 * @param yieldTotal rendimiento total de la tanda (Σ panes / Σ insumo)
 * @param movingAvg  promedio móvil del rendimiento de las últimas N tandas del mismo tipo
 * @param deviation  clasificación del desvío
 */
public record YieldVerdict(
        BigDecimal yieldTotal,
        BigDecimal movingAvg,
        Deviation deviation) {

    /** Clasificación del desvío respecto al promedio móvil ±umbral. */
    public enum Deviation {
        OK, HIGH, LOW
    }
}
