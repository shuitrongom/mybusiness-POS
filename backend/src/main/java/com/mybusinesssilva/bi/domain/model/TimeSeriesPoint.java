package com.mybusinesssilva.bi.domain.model;

import java.math.BigDecimal;

/**
 * Punto de una serie temporal de ventas (por día o por mes), agrupado en zona
 * {@code America/Mexico_City}.
 *
 * @param date    fecha del punto en formato ISO (día {@code yyyy-MM-dd} o mes {@code yyyy-MM})
 * @param tickets número de ventas en el periodo
 * @param revenue ingreso total del periodo
 */
public record TimeSeriesPoint(
        String date,
        long tickets,
        BigDecimal revenue) {
}
