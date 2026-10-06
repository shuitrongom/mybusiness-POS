package com.mybusinesssilva.customers.adapters.out;

import com.mybusinesssilva.customers.application.CustomerService;
import com.mybusinesssilva.sales.domain.port.out.CustomerCreditPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Adaptador que implementa el puerto de crédito requerido por el módulo de ventas, delegando en
 * el servicio de clientes. Así ventas registra el crédito de una venta sin conocer las tablas de
 * clientes ni su servicio directamente (dependencia por interfaz).
 */
@Component
public class CustomerCreditAdapter implements CustomerCreditPort {

    private final CustomerService customerService;

    public CustomerCreditAdapter(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public long registerReceivable(long customerId, long saleId, BigDecimal amount,
                                   long branchId, LocalDate dueDate) {
        return customerService.addReceivable(customerId, saleId, amount, branchId, dueDate);
    }
}
