package com.mybusinesssilva.customers.adapters.in.rest;

import com.mybusinesssilva.customers.application.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Precios especiales por cliente. Al asignar a un cliente un precio distinto para un producto,
 * el punto de venta lo usa como fuente de verdad cuando ese cliente está seleccionado.
 *
 * <p>Requiere el módulo {@code customers} habilitado (igual que {@link CustomerController}); los
 * GET además admiten a quien pueda vender ({@code canReadCustomers()}).
 */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/prices")
@PreAuthorize("@moduleAccess.canUse('customers')")
public class CustomerPriceController {

    private final CustomerService customerService;

    public CustomerPriceController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** Lista los precios especiales del cliente, con el precio normal del producto para comparar. */
    @GetMapping
    @PreAuthorize("@moduleAccess.canReadCustomers()")
    public List<Map<String, Object>> list(@PathVariable long customerId) {
        return customerService.listSpecialPrices(customerId);
    }

    /** Mapa producto -> precio especial, para que el POS resuelva el precio del cliente. */
    @GetMapping("/map")
    @PreAuthorize("@moduleAccess.canReadCustomers()")
    public Map<Long, BigDecimal> map(@PathVariable long customerId) {
        return customerService.specialPriceMap(customerId);
    }

    /** Crea o actualiza el precio especial de un producto para el cliente (idempotente). */
    @PutMapping
    public ResponseEntity<?> upsert(
            @PathVariable long customerId, @Valid @RequestBody UpsertRequest request) {
        try {
            return ResponseEntity.ok(
                    customerService.upsertSpecialPrice(customerId, request.productId(), request.price()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(statusFor(ex)).body(Map.of("error", ex.getMessage()));
        }
    }

    /** Elimina el precio especial de un producto para el cliente. */
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(@PathVariable long customerId, @PathVariable long productId) {
        customerService.deleteSpecialPrice(customerId, productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Un cliente/producto inexistente es un 404; un precio inválido es un 400. Se distingue por el
     * texto del mensaje porque el servicio lanza la misma excepción para ambos casos.
     */
    private static int statusFor(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        return message.startsWith("No existe") ? 404 : 400;
    }

    /** Alta/edición de un precio especial. */
    public record UpsertRequest(
            @NotNull Long productId,
            @NotNull @DecimalMin("0.0") BigDecimal price) {
    }
}
