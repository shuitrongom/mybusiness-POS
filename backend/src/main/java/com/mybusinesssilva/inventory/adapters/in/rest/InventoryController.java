package com.mybusinesssilva.inventory.adapters.in.rest;

import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.domain.model.InventoryMovement;
import com.mybusinesssilva.inventory.domain.model.StockLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mybusinesssilva.platform.security.AuthenticatedUser;

/**
 * Endpoints de inventario. Requieren el módulo {@code inventory} habilitado.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@PreAuthorize("@moduleAccess.canUse('inventory')")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/stock/{productId}/{branchId}")
    public StockLevel stock(@PathVariable long productId, @PathVariable long branchId) {
        return inventoryService.stock(productId, branchId);
    }

    @GetMapping("/kardex/{productId}")
    public List<InventoryMovement> kardex(@PathVariable long productId) {
        return inventoryService.kardex(productId);
    }

    @GetMapping("/alerts/low-stock")
    public List<StockLevel> lowStock() {
        return inventoryService.lowStockAlerts();
    }

    @PostMapping("/adjust")
    public ResponseEntity<BigDecimal> adjust(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody AdjustRequest request) {
        BigDecimal balance = inventoryService.adjust(
                request.productId(), request.branchId(), request.delta(),
                request.reason(), actor == null ? "unknown" : actor.subject());
        return ResponseEntity.ok(balance);
    }

    @PostMapping("/transfer")
    public ResponseEntity<Void> transfer(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody TransferRequest request) {
        inventoryService.transfer(
                request.productId(), request.fromBranchId(), request.toBranchId(),
                request.quantity(), actor == null ? "unknown" : actor.subject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/minimum")
    public ResponseEntity<Void> setMinimum(@Valid @RequestBody MinimumRequest request) {
        inventoryService.setMinimum(request.productId(), request.branchId(), request.minQuantity());
        return ResponseEntity.noContent().build();
    }

    /** Ajuste manual de inventario. */
    public record AdjustRequest(
            @NotNull Long productId,
            @NotNull Long branchId,
            @NotNull BigDecimal delta,
            String reason) {
    }

    /** Traspaso entre sucursales. */
    public record TransferRequest(
            @NotNull Long productId,
            @NotNull Long fromBranchId,
            @NotNull Long toBranchId,
            @NotNull BigDecimal quantity) {
    }

    /** Configuración de stock mínimo. */
    public record MinimumRequest(
            @NotNull Long productId,
            @NotNull Long branchId,
            @NotNull BigDecimal minQuantity) {
    }
}
