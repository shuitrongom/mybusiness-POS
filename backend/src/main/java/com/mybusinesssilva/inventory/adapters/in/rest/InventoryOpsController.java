package com.mybusinesssilva.inventory.adapters.in.rest;

import com.mybusinesssilva.inventory.application.InventoryOpsService;
import com.mybusinesssilva.platform.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de la suite de inventario enterprise (documentos, series, lotes, variantes, conteo
 * físico, calidad y etiquetas). Requieren el módulo {@code inventory} habilitado.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@PreAuthorize("@moduleAccess.canUse('inventory')")
public class InventoryOpsController {

    private final InventoryOpsService service;

    public InventoryOpsController(InventoryOpsService service) {
        this.service = service;
    }

    private static String actorOf(AuthenticatedUser a) {
        return a == null ? "unknown" : a.subject();
    }

    // ---- Catálogos ----

    @GetMapping("/concepts")
    public List<Map<String, Object>> concepts(@RequestParam(value = "direction", required = false) String direction) {
        return service.listConcepts(direction);
    }

    @PostMapping("/concepts")
    public ResponseEntity<Long> createConcept(@Valid @RequestBody ConceptRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createConcept(r.name(), r.direction()));
    }

    @GetMapping("/sizes")
    public List<Map<String, Object>> sizes() { return service.listSizes(); }

    @PostMapping("/sizes")
    public ResponseEntity<Long> createSize(@Valid @RequestBody SizeRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSize(r.code(), r.label()));
    }

    @GetMapping("/colors")
    public List<Map<String, Object>> colors() { return service.listColors(); }

    @PostMapping("/colors")
    public ResponseEntity<Long> createColor(@Valid @RequestBody ColorRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createColor(r.code(), r.label(), r.hex()));
    }

    @GetMapping("/label-formats")
    public List<Map<String, Object>> labelFormats() { return service.listLabelFormats(); }

    // ---- Alta rápida ----

    @PostMapping("/quick-product")
    public ResponseEntity<Long> quickProduct(@Valid @RequestBody QuickProductRequest r) {
        long id = service.quickCreateProduct(r.code(), r.description(), r.cost(), r.price(),
                r.satKey(), r.satUnit(), r.unit(), r.withoutVat() != null && r.withoutVat());
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    // ---- Documentos de entrada / salida ----

    @GetMapping("/documents")
    public List<Map<String, Object>> documents(
            @RequestParam(value = "docType", required = false) String docType,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        return service.listDocuments(docType, limit);
    }

    @PostMapping("/documents")
    public ResponseEntity<Long> registerDocument(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody DocumentRequest r) {
        List<InventoryOpsService.DocLineInput> lines = r.lines().stream()
                .map(l -> new InventoryOpsService.DocLineInput(
                        l.productId(), l.description(), l.quantity(),
                        l.unitCost() == null ? BigDecimal.ZERO : l.unitCost()))
                .toList();
        long id = service.registerDocument(r.docType(), r.conceptId(), r.branchId(), r.notes(),
                lines, actorOf(actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    // ---- Números de serie ----

    @GetMapping("/serials/{productId}")
    public List<Map<String, Object>> serials(
            @PathVariable long productId,
            @RequestParam(value = "status", required = false) String status) {
        return service.listSerials(productId, status);
    }

    @PostMapping("/serials")
    public ResponseEntity<Map<String, Object>> addSerials(@Valid @RequestBody SerialRequest r) {
        int added;
        if (r.rangeFrom() != null && r.rangeTo() != null) {
            added = service.addSerialRange(r.productId(), r.branchId(), r.prefix(),
                    r.rangeFrom(), r.rangeTo(), r.entryPort(), r.pedimento(), r.documentRef());
        } else {
            added = service.addSerials(r.productId(), r.branchId(),
                    r.serials() == null ? List.of() : r.serials(),
                    r.entryPort(), r.pedimento(), r.documentRef());
        }
        return ResponseEntity.ok(Map.of("added", added));
    }

    // ---- Lotes ----

    @GetMapping("/lots")
    public List<Map<String, Object>> lots(
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "onlyWithStock", required = false, defaultValue = "true") boolean onlyWithStock) {
        return service.listLots(productId, onlyWithStock);
    }

    @PostMapping("/lots")
    public ResponseEntity<Long> addLot(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody LotRequest r) {
        long id = service.addLot(r.productId(), r.branchId(), r.lotCode(), r.expiration(),
                r.quantity(), r.entryPort(), r.pedimento(), r.documentRef(), actorOf(actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    // ---- Variantes talla/color ----

    @PostMapping("/variants/generate")
    public ResponseEntity<Map<String, Object>> generateVariants(@Valid @RequestBody VariantRequest r) {
        return ResponseEntity.ok(service.generateVariants(
                r.modelCode(), r.modelName(), r.brand(), r.cost(), r.price1(), r.price2(), r.price3(),
                r.categoryId(), r.sizeCodes(), r.colorCodes()));
    }

    // ---- Inventario físico ----

    @GetMapping("/counts")
    public List<Map<String, Object>> counts(
            @RequestParam(value = "limit", required = false, defaultValue = "30") int limit) {
        return service.listCounts(limit);
    }

    @PostMapping("/counts/start")
    public ResponseEntity<Long> startCount(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody StartCountRequest r) {
        long id = service.startPhysicalCount(r.branchId(), r.categoryId(), actorOf(actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @GetMapping("/counts/{countId}/lines")
    public List<Map<String, Object>> countLines(
            @PathVariable long countId,
            @RequestParam(value = "onlyDifferences", required = false, defaultValue = "false") boolean onlyDifferences) {
        return service.countLines(countId, onlyDifferences);
    }

    @PostMapping("/counts/{countId}/capture")
    public ResponseEntity<Void> capture(@PathVariable long countId, @Valid @RequestBody CaptureRequest r) {
        service.captureCount(countId, r.productId(), r.marbete(), r.counted());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/counts/{countId}/apply")
    public ResponseEntity<Map<String, Object>> applyCount(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long countId) {
        return ResponseEntity.ok(service.applyPhysicalCount(countId, actorOf(actor)));
    }

    // ---- Calidad de inventario ----

    @GetMapping("/quality")
    public List<Map<String, Object>> quality(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = to != null ? to : LocalDate.now();
        return service.inventoryQuality(f, t);
    }

    // ---- Kardex enriquecido ----

    @GetMapping("/kardex-detail/{productId}")
    public List<Map<String, Object>> kardexDetail(
            @PathVariable long productId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit) {
        return service.kardex(productId, limit);
    }

    // ---- StockApp ----

    @PostMapping("/stockapp/import")
    public ResponseEntity<Map<String, Object>> stockAppImport(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody StockAppRequest r) {
        List<InventoryOpsService.StockAppItem> items = r.items().stream()
                .map(i -> new InventoryOpsService.StockAppItem(
                        i.productId(), i.code(), i.quantity(), i.cost()))
                .toList();
        var result = service.importStockApp(
                r.mode() == null || r.mode().isBlank() ? "ENTRY" : r.mode(),
                r.branchId(), items, actorOf(actor));
        return ResponseEntity.ok(result);
    }

    // ---- Etiquetas ----

    @PostMapping("/labels")
    public List<Map<String, Object>> labels(@Valid @RequestBody LabelsRequest r) {
        return service.labelData(r.items().stream()
                .map(i -> new InventoryOpsService.LabelRequestItem(i.productId(), i.copies() <= 0 ? 1 : i.copies()))
                .toList());
    }

    // =====================================================================
    // DTOs
    // =====================================================================

    public record ConceptRequest(@NotBlank String name, @NotBlank String direction) {
    }

    public record SizeRequest(@NotBlank String code, @NotBlank String label) {
    }

    public record ColorRequest(@NotBlank String code, @NotBlank String label, String hex) {
    }

    public record QuickProductRequest(
            String code, @NotBlank String description, BigDecimal cost, BigDecimal price,
            String satKey, String satUnit, String unit, Boolean withoutVat) {
    }

    public record DocumentRequest(
            @NotBlank String docType, Long conceptId, @NotNull Long branchId, String notes,
            @NotEmpty List<DocLineRequest> lines) {
    }

    public record DocLineRequest(
            @NotNull Long productId, String description, @NotNull BigDecimal quantity, BigDecimal unitCost) {
    }

    public record SerialRequest(
            @NotNull Long productId, Long branchId, List<String> serials, String prefix,
            Long rangeFrom, Long rangeTo, String entryPort, String pedimento, String documentRef) {
    }

    public record LotRequest(
            @NotNull Long productId, @NotNull Long branchId, String lotCode,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiration,
            @NotNull BigDecimal quantity, String entryPort, String pedimento, String documentRef) {
    }

    public record VariantRequest(
            @NotBlank String modelCode, @NotBlank String modelName, String brand,
            BigDecimal cost, BigDecimal price1, BigDecimal price2, BigDecimal price3, Long categoryId,
            @NotEmpty List<String> sizeCodes, @NotEmpty List<String> colorCodes) {
    }

    public record StartCountRequest(@NotNull Long branchId, Long categoryId) {
    }

    public record CaptureRequest(@NotNull Long productId, String marbete, @NotNull BigDecimal counted) {
    }

    public record LabelsRequest(@NotEmpty List<LabelItemRequest> items) {
    }

    public record LabelItemRequest(@NotNull Long productId, int copies) {
    }

    public record StockAppRequest(
            String mode, @NotNull Long branchId, @NotEmpty List<StockAppItemRequest> items) {
    }

    public record StockAppItemRequest(Long productId, String code, BigDecimal quantity, BigDecimal cost) {
    }
}
