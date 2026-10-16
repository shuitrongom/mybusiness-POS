package com.mybusinesssilva.bi.domain.model;

import java.math.BigDecimal;

/**
 * Mezcla de ventas por método de pago.
 *
 * @param method     método de pago (CASH, CARD, TRANSFER, VOUCHER)
 * @param operations número de pagos registrados con ese método
 * @param amount     monto total cobrado con ese método
 */
public record PaymentMix(
        String method,
        long operations,
        BigDecimal amount) {
}
