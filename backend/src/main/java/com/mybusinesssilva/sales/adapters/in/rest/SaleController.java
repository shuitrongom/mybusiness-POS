package com.mybusinesssilva.sales.adapters.in.rest;

import com.mybusinesssilva.sales.application.SaleService;
import com.mybusinesssilva.sales.domain.model.Payment;
import com.mybusinesssilva.sales.domain.model.PaymentMethod;
import com.mybusinesssilva.sales.domain.model.Sale;
import com.mybusinesssilva.sales.domain.model.SaleLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mybusinesssilva.platform.security.AuthenticatedUser;

/**
 * Endpoints del punto de venta. Requieren el módulo {@code sales} habilitado.
 */
@RestController
@RequestMapping("/api/v1/sales")
@PreAuthorize("@moduleAccess.canUse('sales')")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    /**
     * Registra una venta. Acepta una clave de idempotencia para sincronización offline: si la
     * misma clave llega dos veces, la venta no se duplica.
     */
    @PostMapping
    public ResponseEntity<SaleService.SaleResult> register(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateSaleRequest request) {

        String cashier = actor == null ? "unknown" : actor.subject();

        List<SaleLine> lines = request.lines().stream()
                .map(l -> new SaleLine(l.productId(), l.description(), l.quantity(),
                        l.unitPrice(), l.discount()))
                .toList();

        List<Payment> payments = request.payments() == null ? List.of()
                : request.payments().stream()
                        .map(p -> new Payment(PaymentMethod.valueOf(p.method()), p.amount()))
                        .toList();

        Sale sale = Sale.complete(request.branchId(), request.cashRegisterId(), request.shiftId(),
                cashier, request.customerId(), lines, payments, request.idempotencyKey());

        SaleService.SaleResult result = saleService.registerSale(sale);
        HttpStatus status = result.duplicated() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<Void> voidSale(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        saleService.voidSale(id, actor == null ? "unknown" : actor.subject());
        return ResponseEntity.noContent().build();
    }

    /** Registra una devolución de productos (reingresa inventario). */
    @PostMapping("/returns")
    public ResponseEntity<Void> registerReturn(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ReturnRequest request) {
        List<SaleService.ReturnItem> items = request.items().stream()
                .map(i -> new SaleService.ReturnItem(i.productId(), i.quantity()))
                .toList();
        saleService.registerReturn(request.branchId(), items,
                actor == null ? "unknown" : actor.subject());
        return ResponseEntity.noContent().build();
    }

    /** Registra una cotización o apartado (no cobra ni descuenta inventario). */
    @PostMapping("/quotes")
    public ResponseEntity<SaleService.SaleResult> registerQuote(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateSaleRequest request) {
        String cashier = actor == null ? "unknown" : actor.subject();
        List<SaleLine> lines = request.lines().stream()
                .map(l -> new SaleLine(l.productId(), l.description(), l.quantity(),
                        l.unitPrice(), l.discount()))
                .toList();
        Sale quote = Sale.quote(request.branchId(), cashier, request.customerId(), lines);
        return ResponseEntity.status(HttpStatus.CREATED).body(saleService.registerQuote(quote));
    }

    // --- DTOs ---

    /** Devolución de productos. */
    public record ReturnRequest(
            @NotNull Long branchId,
            @NotEmpty List<ReturnItemRequest> items) {
    }

    /** Renglón de devolución. */
    public record ReturnItemRequest(
            @NotNull Long productId,
            @NotNull BigDecimal quantity) {
    }

    /** Alta de venta. */
    public record CreateSaleRequest(
            @NotNull Long branchId,
            Long cashRegisterId,
            Long shiftId,
            Long customerId,
            @NotEmpty List<LineRequest> lines,
            List<PaymentRequest> payments,
            String idempotencyKey) {
    }

    /** Renglón de venta. */
    public record LineRequest(
            @NotNull Long productId,
            String description,
            @NotNull BigDecimal quantity,
            @NotNull BigDecimal unitPrice,
            BigDecimal discount) {
    }

    /** Pago de venta. */
    public record PaymentRequest(
            @NotNull String method,
            @NotNull BigDecimal amount) {
    }
}
