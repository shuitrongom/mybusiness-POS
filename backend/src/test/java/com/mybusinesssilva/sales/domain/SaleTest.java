package com.mybusinesssilva.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mybusinesssilva.sales.domain.model.Payment;
import com.mybusinesssilva.sales.domain.model.PaymentMethod;
import com.mybusinesssilva.sales.domain.model.Sale;
import com.mybusinesssilva.sales.domain.model.SaleLine;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de las reglas de la venta: cálculo de totales, cambio, pagos mixtos y
 * validación de pagos insuficientes.
 */
class SaleTest {

    private SaleLine line(String desc, String qty, String price, String discount) {
        return new SaleLine(1L, desc, new BigDecimal(qty), new BigDecimal(price),
                new BigDecimal(discount));
    }

    @Test
    void calculatesTotalsFromLines() {
        Sale sale = Sale.complete(1L, null, null, "cajero", null,
                List.of(
                        new SaleLine(1L, "Refresco", new BigDecimal("2"), new BigDecimal("18.00"),
                                BigDecimal.ZERO),
                        new SaleLine(2L, "Pan", new BigDecimal("3"), new BigDecimal("5.00"),
                                new BigDecimal("2.00"))),
                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("100.00"))),
                null);

        // 2*18 = 36 ; 3*5 - 2 = 13 ; total = 49
        assertThat(sale.total()).isEqualByComparingTo("49.00");
        assertThat(sale.change()).isEqualByComparingTo("51.00");
    }

    @Test
    void supportsMixedPayments() {
        Sale sale = Sale.complete(1L, null, null, "cajero", null,
                List.of(new SaleLine(1L, "Producto", new BigDecimal("1"),
                        new BigDecimal("100.00"), BigDecimal.ZERO)),
                List.of(
                        new Payment(PaymentMethod.CASH, new BigDecimal("60.00")),
                        new Payment(PaymentMethod.CARD, new BigDecimal("40.00"))),
                null);

        assertThat(sale.totalPaid()).isEqualByComparingTo("100.00");
        assertThat(sale.change()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsInsufficientPayment() {
        assertThatThrownBy(() -> Sale.complete(1L, null, null, "cajero", null,
                List.of(new SaleLine(1L, "Producto", new BigDecimal("1"),
                        new BigDecimal("100.00"), BigDecimal.ZERO)),
                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("50.00"))),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no cubren el total");
    }

    @Test
    void rejectsSaleWithoutLines() {
        assertThatThrownBy(() -> Sale.complete(1L, null, null, "cajero", null,
                List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
