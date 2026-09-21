package com.mybusinesssilva.bi.domain.model;

import java.math.BigDecimal;

/**
 * Resumen del tablero principal para un día.
 *
 * @param date          fecha del resumen (ISO)
 * @param salesCount    número de ventas
 * @param salesTotal    total vendido
 * @param averageTicket ticket promedio
 * @param itemsSold     unidades vendidas
 */
public record DashboardSummary(
        String date,
        long salesCount,
        BigDecimal salesTotal,
        BigDecimal averageTicket,
        BigDecimal itemsSold) {
}
