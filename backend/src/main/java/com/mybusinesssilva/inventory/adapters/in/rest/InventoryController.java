package com.mybusinesssilva.inventory.adapters.in.rest;

import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.domain.model.InventoryMovement;
import com.mybusinesssilva.inventory.domain.model.StockLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final JdbcClient jdbc;

    public InventoryController(InventoryService inventoryService, JdbcClient jdbc) {
        this.inventoryService = inventoryService;
        this.jdbc = jdbc;
    }

    /**
     * Existencias de todos los productos con su nombre, categoría, unidad, costo y valor de
     * inventario, opcionalmente filtrado por sucursal. Es la fuente de la tabla de inventario.
     */
    @GetMapping("/stock")
    public List<Map<String, Object>> stockOverview(
            @RequestParam(value = "branchId", required = false) Long branchId) {
        return jdbc.sql("""
                SELECT s.product_id, s.branch_id, b.name AS branch_name,
                       p.name AS product_name, p.unit, p.sku,
                       c.name AS category_name,
                       s.quantity, s.min_quantity, p.cost,
                       (s.quantity * p.cost) AS stock_value
                FROM inventory_stock s
                JOIN product p ON p.id = s.product_id
                LEFT JOIN category c ON c.id = p.category_id
                LEFT JOIN branch b ON b.id = s.branch_id
                WHERE (CAST(:branchId AS bigint) IS NULL OR s.branch_id = :branchId)
                ORDER BY p.name
                """)
                .param("branchId", branchId)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("productId", rs.getLong("product_id"));
                    m.put("branchId", rs.getLong("branch_id"));
                    m.put("branchName", rs.getString("branch_name"));
                    m.put("productName", rs.getString("product_name"));
                    m.put("sku", rs.getString("sku"));
                    m.put("unit", rs.getString("unit"));
                    m.put("categoryName", rs.getString("category_name"));
                    m.put("quantity", rs.getBigDecimal("quantity"));
                    m.put("minQuantity", rs.getBigDecimal("min_quantity"));
                    m.put("cost", rs.getBigDecimal("cost"));
                    m.put("stockValue", rs.getBigDecimal("stock_value"));
                    return m;
                })
                .list();
    }

    /** Resumen (KPIs) del inventario: SKUs, unidades totales, valor y número de alertas. */
    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam(value = "branchId", required = false) Long branchId) {
        return jdbc.sql("""
                SELECT count(*) AS skus,
                       COALESCE(sum(s.quantity), 0) AS units,
                       COALESCE(sum(s.quantity * p.cost), 0) AS value,
                       COALESCE(sum(CASE WHEN s.quantity <= s.min_quantity AND s.min_quantity > 0
                                         THEN 1 ELSE 0 END), 0) AS alerts
                FROM inventory_stock s
                JOIN product p ON p.id = s.product_id
                WHERE (CAST(:branchId AS bigint) IS NULL OR s.branch_id = :branchId)
                """)
                .param("branchId", branchId)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("skus", rs.getLong("skus"));
                    m.put("units", rs.getBigDecimal("units"));
                    m.put("value", rs.getBigDecimal("value"));
                    m.put("alerts", rs.getLong("alerts"));
                    return m;
                })
                .single();
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
