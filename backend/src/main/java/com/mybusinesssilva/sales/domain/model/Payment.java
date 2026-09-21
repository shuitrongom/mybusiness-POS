package com.mybusinesssilva.sales.domain.model;

import java.math.BigDecimal;

/**
 * Pago de una venta. Una venta puede tener varios pagos (pagos mixtos).
 *
 * @param method método de pago
 * @param amount importe pagado con ese método
 */
public record Payment(PaymentMethod method, BigDecimal amount) {

    public Payment {
        if (method == null) {
            throw new IllegalArgumentException("El pago requiere método");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El importe del pago debe ser positivo");
        }
    }
}
