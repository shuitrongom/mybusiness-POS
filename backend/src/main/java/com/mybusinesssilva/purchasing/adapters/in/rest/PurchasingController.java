package com.mybusinesssilva.purchasing.adapters.in.rest;

import com.mybusinesssilva.purchasing.application.PurchasingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
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
 * Endpoints de compras y proveedores. Requieren el módulo {@code purchasing} habilitado.
 */
@RestController
@RequestMapping("/api/v1/purchasing")
@PreAuthorize("@moduleAccess.canUse('purchasing')")
public class PurchasingController {

    private final PurchasingService purchasingService;

    public PurchasingController(PurchasingService purchasingService) {
        this.purchasingService = purchasingService;
    }

    @PostMapping("/suppliers")
    public ResponseEntity<Long> createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        long id = purchasingService.createSupplier(
                request.name(), request.rfc(), request.phone(), request.email());
        return ResponseEntity.ok(id);
    }

    @PostMapping("/purchases")
    public ResponseEntity<Long> receivePurchase(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ReceivePurchaseRequest request) {
        List<PurchasingService.PurchaseLineInput> lines = request.lines().stream()
                .map(l -> new PurchasingService.PurchaseLineInput(
                        l.productId(), l.quantity(), l.unitCost()))
                .toList();
        long purchaseId = purchasingService.receivePurchase(
                request.supplierId(), request.branchId(), request.invoiceRef(),
                request.onCredit(), lines, actor == null ? "unknown" : actor.subject());
        return ResponseEntity.ok(purchaseId);
    }

    @PostMapping("/payables/{id}/pay")
    public ResponseEntity<Void> payPayable(
            @PathVariable long id, @Valid @RequestBody PayPayableRequest request) {
        purchasingService.payPayable(id, request.amount());
        return ResponseEntity.noContent().build();
    }

    /** Alta de proveedor. */
    public record CreateSupplierRequest(
            @NotNull String name, String rfc, String phone, String email) {
    }

    /** Recepción de compra. */
    public record ReceivePurchaseRequest(
            @NotNull Long supplierId,
            @NotNull Long branchId,
            String invoiceRef,
            boolean onCredit,
            @NotEmpty List<LineRequest> lines) {
    }

    /** Renglón de compra. */
    public record LineRequest(
            @NotNull Long productId,
            @NotNull BigDecimal quantity,
            @NotNull BigDecimal unitCost) {
    }

    /** Abono a cuenta por pagar. */
    public record PayPayableRequest(@NotNull BigDecimal amount) {
    }
}
