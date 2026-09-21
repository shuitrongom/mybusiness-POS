package com.mybusinesssilva.customers.adapters.in.rest;

import com.mybusinesssilva.customers.application.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del módulo de clientes/CRM. Requieren el módulo {@code customers} habilitado.
 *
 * <p>Expone el alta de clientes, la gestión de cuentas por cobrar (crédito) y las operaciones
 * del programa de lealtad (acumular, canjear y consultar puntos).
 */
@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("@moduleAccess.canUse('customers')")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<Long> create(@Valid @RequestBody CreateCustomerRequest request) {
        long id = customerService.createCustomer(
                request.name(), request.rfc(), request.phone(), request.email(),
                request.creditLimit());
        return ResponseEntity.ok(id);
    }

    @PostMapping("/{id}/receivables")
    public ResponseEntity<Long> addReceivable(
            @PathVariable long id,
            @Valid @RequestBody AddReceivableRequest request) {
        long receivableId = customerService.addReceivable(
                id, request.saleId(), request.amount(), request.dueDate());
        return ResponseEntity.ok(receivableId);
    }

    @PostMapping("/receivables/{receivableId}/pay")
    public ResponseEntity<Void> payReceivable(
            @PathVariable long receivableId,
            @Valid @RequestBody PayReceivableRequest request) {
        customerService.payReceivable(receivableId, request.amount());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/loyalty/earn")
    public ResponseEntity<BigDecimal> earnLoyalty(
            @PathVariable long id,
            @Valid @RequestBody LoyaltyRequest request) {
        BigDecimal balance = customerService.earnLoyalty(id, request.amount(), request.reference());
        return ResponseEntity.ok(balance);
    }

    @PostMapping("/{id}/loyalty/redeem")
    public ResponseEntity<BigDecimal> redeemLoyalty(
            @PathVariable long id,
            @Valid @RequestBody LoyaltyRequest request) {
        BigDecimal balance = customerService.redeemLoyalty(id, request.amount(), request.reference());
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/{id}/loyalty")
    public ResponseEntity<Map<String, BigDecimal>> loyaltyBalance(@PathVariable long id) {
        return ResponseEntity.ok(Map.of("balance", customerService.loyaltyBalance(id)));
    }

    /** Alta de cliente. */
    public record CreateCustomerRequest(
            @NotBlank String name,
            String rfc,
            String phone,
            String email,
            BigDecimal creditLimit) {
    }

    /** Generación de una cuenta por cobrar a partir de una venta a crédito. */
    public record AddReceivableRequest(
            @NotNull Long saleId,
            @NotNull BigDecimal amount,
            LocalDate dueDate) {
    }

    /** Abono a una cuenta por cobrar. */
    public record PayReceivableRequest(
            @NotNull BigDecimal amount) {
    }

    /** Movimiento de lealtad (acumular o canjear). */
    public record LoyaltyRequest(
            @NotNull @Min(0) BigDecimal amount,
            String reference) {
    }
}
