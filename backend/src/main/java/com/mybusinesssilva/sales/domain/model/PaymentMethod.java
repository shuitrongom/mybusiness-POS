package com.mybusinesssilva.sales.domain.model;

/**
 * Método de pago de una venta.
 */
public enum PaymentMethod {
    /** Efectivo. */
    CASH,
    /** Tarjeta (débito/crédito). */
    CARD,
    /** Transferencia electrónica. */
    TRANSFER,
    /** Vale o monedero. */
    VOUCHER
}
