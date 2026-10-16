package com.mybusinesssilva.bi.domain.model;

import java.math.BigDecimal;

/**
 * Comparación de un periodo contra el periodo anterior equivalente.
 *
 * @param current   valor del periodo actual
 * @param previous  valor del periodo anterior
 * @param changePct variación porcentual respecto al anterior ({@code (current-previous)/previous*100})
 */
public record PeriodDelta(
        BigDecimal current,
        BigDecimal previous,
        BigDecimal changePct) {
}
