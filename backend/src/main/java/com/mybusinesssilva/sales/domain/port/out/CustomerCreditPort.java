package com.mybusinesssilva.sales.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Puerto de salida que el módulo de ventas usa para registrar el crédito de una venta sin
 * acoplarse al módulo de clientes. La implementación vive en la capa de infraestructura y
 * delega en el servicio de clientes (cuentas por cobrar).
 */
public interface CustomerCreditPort {

    /**
     * Genera una cuenta por cobrar para el cliente por el saldo no pagado de una venta a crédito.
     * Debe validar el límite de crédito del cliente y actualizar su crédito utilizado.
     *
     * @param customerId cliente al que se le otorga el crédito
     * @param saleId     venta que origina la cuenta por cobrar
     * @param amount     saldo a crédito (debe ser positivo)
     * @param branchId   sucursal de origen (para cobranza por sucursal)
     * @param dueDate    fecha de vencimiento (puede ser null)
     * @return id de la cuenta por cobrar creada
     */
    long registerReceivable(long customerId, long saleId, BigDecimal amount,
                            long branchId, LocalDate dueDate);
}
