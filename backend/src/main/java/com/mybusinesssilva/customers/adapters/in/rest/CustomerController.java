package com.mybusinesssilva.customers.adapters.in.rest;

import com.mybusinesssilva.customers.application.CustomerService;
import com.mybusinesssilva.customers.domain.model.Customer;
import com.mybusinesssilva.platform.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Lista/busca clientes activos. Accesible para vender (asignar cliente en el POS) o para
     * administrar el CRM. El texto de búsqueda es opcional.
     */
    @GetMapping
    @PreAuthorize("@moduleAccess.canReadCustomers()")
    public List<Map<String, Object>> list(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return customerService.listCustomers(query, limit);
    }

    @PostMapping
    public ResponseEntity<Long> create(@Valid @RequestBody CreateCustomerRequest request) {
        long id = customerService.createCustomer(
                request.name(), request.rfc(), request.phone(), request.email(),
                request.creditLimit());
        return ResponseEntity.ok(id);
    }

    /** Obtiene el detalle completo de un cliente (datos fiscales, comerciales y direcciones). */
    @GetMapping("/{id}")
    @PreAuthorize("@moduleAccess.canReadCustomers()")
    public ResponseEntity<Customer> getById(@PathVariable long id) {
        return customerService.getCustomer(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Alta de cliente ENTERPRISE con datos fiscales CFDI 4.0, comerciales y direcciones. */
    @PostMapping("/full")
    public ResponseEntity<Long> createFull(@Valid @RequestBody CustomerRequest request) {
        long id = customerService.createCustomerFull(request.toCustomer(null));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /** Edición completa de un cliente enterprise. */
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable long id, @Valid @RequestBody CustomerRequest request) {
        customerService.updateCustomer(request.toCustomer(id));
        return ResponseEntity.noContent().build();
    }

    /** Desactiva (borrado lógico) un cliente para preservar su histórico. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable long id) {
        customerService.deactivateCustomer(id);
        return ResponseEntity.noContent().build();
    }

    /** Importa clientes en lote (por ejemplo desde un Excel/CSV convertido en el frontend). */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importBatch(@Valid @RequestBody ImportRequest request) {
        int created = 0;
        int failed = 0;
        for (CustomerRequest c : request.customers()) {
            try {
                customerService.createCustomerFull(c.toCustomer(null));
                created++;
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        return ResponseEntity.ok(Map.of("created", created, "failed", failed,
                "total", request.customers().size()));
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

    /** Lista las cuentas por cobrar (cobranza), filtrables por estado y cliente. */
    @GetMapping("/receivables")
    public List<Map<String, Object>> listReceivables(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "customerId", required = false) Long customerId) {
        return customerService.listReceivables(status, customerId);
    }

    /** Resumen de cobranza: total pendiente, cuentas abiertas y vencidas. */
    @GetMapping("/receivables/summary")
    public Map<String, Object> receivablesSummary() {
        return customerService.receivablesSummary();
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

    // ---- Anticipos ----

    /** Consulta el saldo de anticipo del cliente. */
    @GetMapping("/{id}/advance")
    public ResponseEntity<Map<String, BigDecimal>> advanceBalance(@PathVariable long id) {
        return ResponseEntity.ok(Map.of("balance", customerService.advanceBalance(id)));
    }

    /** Historial de movimientos de anticipo del cliente. */
    @GetMapping("/{id}/advance/movements")
    public List<Map<String, Object>> advanceMovements(
            @PathVariable long id,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        return customerService.advanceMovements(id, limit);
    }

    /** Registra un depósito de anticipo (dinero entregado por adelantado). */
    @PostMapping("/{id}/advance/deposit")
    public ResponseEntity<BigDecimal> depositAdvance(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id, @Valid @RequestBody AdvanceRequest request) {
        BigDecimal balance = customerService.depositAdvance(
                id, request.amount(), request.method(), request.reference(),
                actor == null ? "unknown" : actor.subject());
        return ResponseEntity.ok(balance);
    }

    /** Aplica parte del anticipo a una venta. */
    @PostMapping("/{id}/advance/apply")
    public ResponseEntity<BigDecimal> applyAdvance(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id, @Valid @RequestBody AdvanceApplyRequest request) {
        BigDecimal balance = customerService.applyAdvance(
                id, request.amount(), request.saleId(), request.reference(),
                actor == null ? "unknown" : actor.subject());
        return ResponseEntity.ok(balance);
    }

    /** Devuelve (reembolsa) saldo de anticipo al cliente. */
    @PostMapping("/{id}/advance/refund")
    public ResponseEntity<BigDecimal> refundAdvance(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id, @Valid @RequestBody AdvanceRequest request) {
        BigDecimal balance = customerService.refundAdvance(
                id, request.amount(), request.reference(),
                actor == null ? "unknown" : actor.subject());
        return ResponseEntity.ok(balance);
    }

    /** Alta de cliente (versión simple, retrocompatible). */
    public record CreateCustomerRequest(
            @NotBlank String name,
            String rfc,
            String phone,
            String email,
            BigDecimal creditLimit) {
    }

    /**
     * Alta/edición de cliente ENTERPRISE. Solo el nombre es obligatorio; el resto de datos
     * fiscales/comerciales son opcionales y se validan al momento de facturar.
     */
    public record CustomerRequest(
            @NotBlank String name,
            String legalName,
            String personType,
            String rfc,
            String taxRegime,
            String cfdiUse,
            String zipCode,
            String phone,
            String mobile,
            String email,
            String contactName,
            String salesperson,
            BigDecimal creditLimit,
            Integer creditDays,
            Integer defaultPriceList,
            String classification,
            String externalCode,
            String notes,
            String imageUrl,
            Boolean active,
            List<AddressRequest> addresses) {

        /** Convierte el request en el modelo de dominio con el id dado (null para alta). */
        Customer toCustomer(Long id) {
            List<Customer.CustomerAddress> addrs = addresses == null ? List.of()
                    : addresses.stream().map(AddressRequest::toAddress).toList();
            return new Customer(
                    id, name, legalName, personType, rfc, taxRegime, cfdiUse, zipCode,
                    phone, mobile, email, contactName, salesperson,
                    creditLimit, BigDecimal.ZERO,
                    creditDays == null ? 0 : creditDays,
                    defaultPriceList == null ? 1 : defaultPriceList,
                    classification, externalCode, notes, imageUrl,
                    active == null ? true : active, addrs);
        }
    }

    /** Dirección del cliente en el request. */
    public record AddressRequest(
            String kind,
            String label,
            String street,
            String extNumber,
            String intNumber,
            String neighborhood,
            String city,
            String state,
            String zipCode,
            String country,
            String reference,
            Boolean isDefault) {

        Customer.CustomerAddress toAddress() {
            return new Customer.CustomerAddress(
                    null, kind, label, street, extNumber, intNumber, neighborhood,
                    city, state, zipCode, country, reference, isDefault != null && isDefault);
        }
    }

    /** Importación de clientes en lote. */
    public record ImportRequest(@NotEmpty List<CustomerRequest> customers) {
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

    /** Depósito o reembolso de anticipo. */
    public record AdvanceRequest(
            @NotNull BigDecimal amount,
            String method,
            String reference) {
    }

    /** Aplicación de anticipo a una venta. */
    public record AdvanceApplyRequest(
            @NotNull BigDecimal amount,
            Long saleId,
            String reference) {
    }
}
