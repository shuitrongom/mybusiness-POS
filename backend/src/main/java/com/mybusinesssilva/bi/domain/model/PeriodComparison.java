package com.mybusinesssilva.bi.domain.model;

/**
 * Comparativa de periodos: hoy vs ayer, semana actual vs anterior, mes actual vs anterior.
 *
 * @param today hoy contra ayer
 * @param week  semana actual contra la anterior
 * @param month mes actual contra el anterior
 */
public record PeriodComparison(
        PeriodDelta today,
        PeriodDelta week,
        PeriodDelta month) {
}
