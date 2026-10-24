package com.mybusinesssilva.inventory.bakery.adapters.in.rest;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryEntryStatus;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProductionLine;
import com.mybusinesssilva.inventory.bakery.domain.model.WasteOrigin;
import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.platform.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del subsistema de panadería. Requieren el módulo {@code bakery}
 * habilitado
 * (aislamiento por giro: un negocio de otro giro recibe 403).
 */
@RestController
@RequestMapping("/api/v1/bakery")
@PreAuthorize("@moduleAccess.canUse('bakery')")
public class BakeryController {

    private final BakeryUseCase bakery;

    public BakeryController(BakeryUseCase bakery) {
        this.bakery = bakery;
    }

    // ---- Estatus de entrada ----

    @GetMapping("/entry-statuses")
    public List<BakeryEntryStatus> entryStatuses() {
        return bakery.listEntryStatuses();
    }

    @PostMapping("/entry-statuses")
    public ResponseEntity<Long> createEntryStatus(@Valid @RequestBody EntryStatusRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bakery.createEntryStatus(r.code(), r.label()));
    }

    // ---- Producción ----

    @PostMapping("/productions")
    public ResponseEntity<Long> registerProduction(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ProductionRequest r) {
        List<BakeryProductionLine> lines = r.lines().stream()
                .map(l -> {
                    BigDecimal qty = l.quantity();
                    BigDecimal entry = l.entryPrice() == null ? BigDecimal.ZERO : l.entryPrice();
                    BigDecimal cost = l.unitCost() == null ? BigDecimal.ZERO : l.unitCost();
                    return new BakeryProductionLine(null, l.productId(), l.description(),
                            qty, l.unit(), entry, cost, entry.multiply(qty));
                })
                .toList();
        BigDecimal totalQty = lines.stream().map(BakeryProductionLine::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalValue = lines.stream().map(BakeryProductionLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BakeryProduction production = new BakeryProduction(
                null, null, r.branchId(),
                r.statusCode() == null || r.statusCode().isBlank() ? "PRODUCED" : r.statusCode(),
                null, totalQty, totalValue, r.notes(),
                actorOf(actor), createdRole(actor), null, lines);

        return ResponseEntity.status(HttpStatus.CREATED).body(bakery.registerProduction(production));
    }

    @GetMapping("/productions")
    public List<Map<String, Object>> productions(
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bakery.listRecentProductions(branchId, from, to, limit);
    }

    // ---- Existencia por día / rango ----

    @GetMapping("/stock/daily")
    public Map<String, Object> stockDaily(
            @RequestParam("productId") long productId,
            @RequestParam("branchId") long branchId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bakery.dailyStock(productId, branchId, date);
    }

    @GetMapping("/stock/range")
    public Map<String, Object> stockRange(
            @RequestParam("productId") long productId,
            @RequestParam("branchId") long branchId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "groupBy", required = false) String groupBy) {
        return bakery.rangeStock(productId, branchId, from, to, groupBy);
    }

    /**
     * Existencias del DÍA para TODOS los artículos de pan de la sucursal (una sola
     * vista):
     * producido / vendido / en merma / restante / valor por producto. Si no se
     * envía fecha, hoy.
     */
    @GetMapping("/stock/day")
    public List<Map<String, Object>> stockDay(
            @RequestParam("branchId") long branchId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bakery.dailyStockAll(branchId, date);
    }

    // ---- Artículos de pan ----

    @GetMapping("/items/search")
    public List<Map<String, Object>> itemsSearch(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "branchId", required = false) Long branchId) {
        return bakery.searchBakeryItems(q, branchId);
    }

    @GetMapping("/items")
    public List<Map<String, Object>> items(
            @RequestParam(value = "branchId", required = false) Long branchId) {
        return bakery.listBakeryItems(branchId);
    }

    // ---- Mermas (solo-lectura, trazabilidad para el admin) ----

    @GetMapping("/waste")
    public List<Map<String, Object>> waste(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "productId", required = false) Long productId) {
        return bakery.listWasteMovements(from, to, productId);
    }

    /**
     * Historial UNIFICADO de "Salidas recientes" de panadería: salida normal
     * (inventory_document OUT con su concepto) + merma MANUAL ("Paso a merma") +
     * merma
     * REPARTO ("Reparto de merma"), ordenado por fecha DESC y limitado por
     * {@code limit}.
     * Cada fila trae su tipo en {@code kind}/{@code concept} para que la pantalla
     * muestre
     * qué clase de salida es (resuelve el hueco de que la merma MANUAL/REPARTO no
     * aparecía).
     */
    @GetMapping("/exits")
    public List<Map<String, Object>> exits(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        return bakery.listUnifiedExits(branchId, from, to, limit);
    }

    /**
     * Existencia actual EN MERMA de un producto en una sucursal (para que Salidas
     * muestre cuánta
     * merma hay disponible antes de registrar un reparto).
     */
    @GetMapping("/waste/stock")
    public Map<String, Object> wasteStock(
            @RequestParam("productId") long productId,
            @RequestParam("branchId") long branchId) {
        return Map.of("productId", productId, "branchId", branchId,
                "quantity", bakery.wasteStock(productId, branchId));
    }

    /**
     * Transición de merma desde Salidas: merma MANUAL anticipada (del stock normal
     * a merma) o
     * salida por REPARTO (de la existencia de merma a un repartidor). Los orígenes
     * CIERRE
     * (automático al cerrar caja) y VENTA (POS) NO se aceptan aquí.
     */
    /**
     * Productos de pan con existencia &gt; 0 para el selector de Salidas.
     * {@code kind=normal}
     * (merma manual / salida normal) o {@code kind=waste} (reparto de merma). Así
     * el cajero solo
     * ve lo que SÍ tiene inventario y no elige panes sin existencia.
     */
    @GetMapping("/stock/available")
    public List<Map<String, Object>> stockAvailable(
            @RequestParam("branchId") long branchId,
            @RequestParam(value = "kind", required = false, defaultValue = "normal") String kind) {
        return bakery.productsWithStock(branchId, kind);
    }

    @PostMapping("/waste/transfer")
    public ResponseEntity<Void> transferWaste(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody WasteTransferRequest r) {
        WasteOrigin origin;
        try {
            origin = WasteOrigin.valueOf(r.origin());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Origen de merma inválido: " + r.origin());
        }
        if (origin != WasteOrigin.MANUAL && origin != WasteOrigin.REPARTO) {
            throw new IllegalArgumentException(
                    "Desde Salidas solo se permite merma MANUAL o salida por REPARTO.");
        }
        bakery.transferToWaste(r.productId(), r.branchId(), r.quantity(),
                origin, actorOf(actor), null, null);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static String actorOf(AuthenticatedUser a) {
        return a == null ? "unknown" : a.subject();
    }

    /**
     * Rol operativo principal del actor por precedencia OWNER > ADMIN > SUPERVISOR
     * > CASHIER;
     * {@code null} si no coincide ninguno (lista de roles vacía o solo roles no
     * operativos).
     */
    private static String createdRole(AuthenticatedUser a) {
        if (a == null) {
            return null;
        }
        if (a.hasRole(Roles.OWNER)) {
            return Roles.OWNER;
        }
        if (a.hasRole(Roles.ADMIN)) {
            return Roles.ADMIN;
        }
        if (a.hasRole(Roles.SUPERVISOR)) {
            return Roles.SUPERVISOR;
        }
        if (a.hasRole(Roles.CASHIER)) {
            return Roles.CASHIER;
        }
        return null;
    }

    // =====================================================================
    // DTOs
    // =====================================================================

    public record EntryStatusRequest(@NotBlank String code, @NotBlank String label) {
    }

    public record ProductionRequest(
            @NotNull Long branchId,
            String statusCode,
            String notes,
            @NotEmpty List<ProductionLineRequest> lines) {
    }

    public record ProductionLineRequest(
            @NotNull Long productId,
            String description,
            @NotNull @Positive @DecimalMax("999999999.999") BigDecimal quantity,
            String unit,
            @PositiveOrZero BigDecimal entryPrice,
            @PositiveOrZero BigDecimal unitCost) {
    }

    /**
     * Transición de merma desde Salidas. {@code origin} debe ser {@code MANUAL}
     * (pasar pan del
     * stock normal a merma) o {@code REPARTO} (sacar merma hacia un repartidor).
     */
    public record WasteTransferRequest(
            @NotNull Long productId,
            @NotNull Long branchId,
            @NotNull @Positive @DecimalMax("999999999.999") BigDecimal quantity,
            @NotBlank String origin) {
    }
}
