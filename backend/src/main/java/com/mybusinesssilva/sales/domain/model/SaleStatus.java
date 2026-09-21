package com.mybusinesssilva.sales.domain.model;

/**
 * Estado de una venta.
 */
public enum SaleStatus {
    /** Venta completada (cobrada). */
    COMPLETED,
    /** Venta cancelada. */
    VOIDED,
    /** Cotización (no afecta inventario ni caja). */
    QUOTE
}
