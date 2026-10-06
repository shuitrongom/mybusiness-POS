package com.mybusinesssilva.purchasing.adapters.in.rest;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.purchasing.application.PurchasingService;
import com.mybusinesssilva.purchasing.domain.model.MexicanTax;
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
 * Endpoints de la suite de compras y proveedores. Requieren el módulo {@code purchasing}
 * habilitado. Cubren proveedores, órdenes de compra, recepción con impuestos mexicanos,
 * devoluciones de compra, cuentas por pagar, visitas de proveedor e importación de CFDI.
 */
@RestController
@RequestMapping("/api/v1/purchasing")
@PreAuthorize("@moduleAccess.canUse('purchasing')")
public class PurchasingController {

    private final PurchasingService purchasingService;

    public PurchasingController(PurchasingService purchasingService) {
        this.purchasingService = purchasingService;
    }

    private static String actorOf(AuthenticatedUser actor) {
        return actor == null ? "unknown" : actor.subject();
    }

    // ---- Catálogo de impuestos MX ----

    /** Tasas de IVA válidas en México para poblar los selectores del frontend. */
    @GetMapping("/tax-rates")
    public List<MexicanTax.VatRate> taxRates() {
        return MexicanTax.vatRates();
    }

    // ---- Proveedores ----

    @GetMapping("/suppliers")
    public List<Map<String, Object>> listSuppliers(
            @RequestParam(value = "q", required = false) String query) {
        return purchasingService.listSuppliers(query);
    }

    @GetMapping("/suppliers/{id}")
    public Map<String, Object> getSupplier(@PathVariable long id) {
        return purchasingService.getSupplier(id);
    }

    /** Alta simple (retrocompatible). */
    @PostMapping("/suppliers")
    public ResponseEntity<Long> createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        long id = purchasingService.createSupplier(
                request.name(), request.rfc(), request.phone(), request.email());
        return ResponseEntity.ok(id);
    }

    /** Alta enterprise de proveedor con todos los datos y contactos. */
    @PostMapping("/suppliers/full")
    public ResponseEntity<Long> createSupplierFull(@Valid @RequestBody SupplierRequest request) {
        long id = purchasingService.saveSupplier(null, request.toInput());
        purchasingService.replaceContacts(id, request.contactInputs());
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @PutMapping("/suppliers/{id}")
    public ResponseEntity<Void> updateSupplier(
            @PathVariable long id, @Valid @RequestBody SupplierRequest request) {
        purchasingService.saveSupplier(id, request.toInput());
        purchasingService.replaceContacts(id, request.contactInputs());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<Void> deactivateSupplier(@PathVariable long id) {
        purchasingService.deactivateSupplier(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/suppliers/import")
    public ResponseEntity<Map<String, Object>> importSuppliers(@Valid @RequestBody ImportSuppliersRequest request) {
        return ResponseEntity.ok(purchasingService.importSuppliers(
                request.suppliers().stream().map(SupplierRequest::toInput).toList()));
    }

    // ---- Órdenes de compra y compras ----

    @GetMapping("/purchases")
    public List<Map<String, Object>> listPurchases(
            @RequestParam(value = "docType", required = false) String docType,
            @RequestParam(value = "supplierId", required = false) Long supplierId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return purchasingService.listPurchases(docType, supplierId, from, to);
    }

    /** Recepción de compra simple (retrocompatible). */
    @PostMapping("/purchases")
    public ResponseEntity<Long> receivePurchase(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ReceivePurchaseRequest request) {
        List<PurchasingService.PurchaseLineInput> lines = request.lines().stream()
                .map(l -> new PurchasingService.PurchaseLineInput(l.productId(), l.quantity(), l.unitCost()))
                .toList();
        long id = purchasingService.receivePurchase(request.supplierId(), request.branchId(),
                request.invoiceRef(), request.onCredit(), lines, actorOf(actor));
        return ResponseEntity.ok(id);
    }

    /** Compra enterprise (con impuestos, retención, donativo). */
    @PostMapping("/purchases/full")
    public ResponseEntity<Long> receivePurchaseFull(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody PurchaseRequest request) {
        long id = purchasingService.receivePurchaseFull(request.toInput(), actorOf(actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /** Crea una orden de compra (no afecta inventario hasta recibirse). */
    @PostMapping("/orders")
    public ResponseEntity<Long> createOrder(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody PurchaseRequest request) {
        long id = purchasingService.createOrder(request.toInput(), actorOf(actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /** Convierte una orden de compra en recepción. */
    @PostMapping("/orders/{id}/receive")
    public ResponseEntity<Void> receiveOrder(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        purchasingService.receiveOrder(id, actorOf(actor));
        return ResponseEntity.noContent().build();
    }

    // ---- Devoluciones de compra ----

    @GetMapping("/returns")
    public List<Map<String, Object>> listReturns(
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        return purchasingService.listPurchaseReturns(limit);
    }

    @PostMapping("/returns")
    public ResponseEntity<Long> registerReturn(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody PurchaseReturnRequest request) {
        List<PurchasingService.ReturnLineInput> items = request.items().stream()
                .map(i -> new PurchasingService.ReturnLineInput(
                        i.productId(), i.description(), i.quantity(), i.unitCost()))
                .toList();
        long id = purchasingService.registerPurchaseReturn(
                request.purchaseId(), request.supplierId(), request.branchId(),
                request.reason(), items, actorOf(actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    // ---- Cuentas por pagar ----

    @GetMapping("/payables")
    public List<Map<String, Object>> listPayables(
            @RequestParam(value = "supplierId", required = false) Long supplierId,
            @RequestParam(value = "onlyWithBalance", required = false, defaultValue = "true") boolean onlyWithBalance) {
        return purchasingService.listPayables(supplierId, onlyWithBalance);
    }

    @GetMapping("/payables/summary")
    public Map<String, Object> payablesSummary() {
        return purchasingService.payablesSummary();
    }

    @PostMapping("/payables/{id}/pay")
    public ResponseEntity<Void> payPayable(
            @PathVariable long id, @Valid @RequestBody PayPayableRequest request) {
        purchasingService.payPayable(id, request.amount());
        return ResponseEntity.noContent().build();
    }

    // ---- Visitas de proveedor ----

    @PostMapping("/suppliers/{id}/visit-role")
    public ResponseEntity<Void> setVisitRole(
            @PathVariable long id, @Valid @RequestBody VisitRoleRequest request) {
        purchasingService.setVisitRole(id, request.periodicity(), request.days());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/visits")
    public List<Map<String, Object>> listVisits(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return purchasingService.listVisits(from, to);
    }

    @PostMapping("/visits")
    public ResponseEntity<Long> scheduleVisit(@Valid @RequestBody ScheduleVisitRequest request) {
        long id = purchasingService.scheduleVisit(
                request.supplierId(), request.date(), request.time(),
                request.estimatedAmount(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @PostMapping("/visits/{id}/visited")
    public ResponseEntity<Void> markVisited(
            @PathVariable long id, @RequestBody(required = false) MarkVisitedRequest request) {
        purchasingService.markVisited(id, request == null ? null : request.purchaseAmount());
        return ResponseEntity.noContent().build();
    }

    // ---- Importación de CFDI XML ----

    @PostMapping("/cfdi/parse")
    public Map<String, Object> parseCfdi(@Valid @RequestBody CfdiParseRequest request) {
        return purchasingService.parseCfdi(request.xml());
    }

    // =====================================================================
    // DTOs
    // =====================================================================

    public record CreateSupplierRequest(
            @NotNull String name, String rfc, String phone, String email) {
    }

    public record SupplierRequest(
            String externalCode, @NotBlank String name, String rfc, String phone, String email,
            String country, String zipCode, String street, String neighborhood, String town,
            String city, String state, Integer creditDays, BigDecimal creditLimit,
            BigDecimal discount1, BigDecimal discount2, BigDecimal discount3, BigDecimal discount4,
            BigDecimal discount5, String classification, String reviewPayment,
            Boolean affectsInventoryOnly, Boolean skipPayable, String notes, String imageUrl,
            String visitPeriodicity, String visitDays, List<ContactRequest> contacts) {

        PurchasingService.SupplierInput toInput() {
            return new PurchasingService.SupplierInput(
                    externalCode, name, rfc, phone, email, country, zipCode, street, neighborhood,
                    town, city, state, creditDays == null ? 0 : creditDays,
                    nz(creditLimit), nz(discount1), nz(discount2), nz(discount3), nz(discount4),
                    nz(discount5), classification, reviewPayment,
                    affectsInventoryOnly != null && affectsInventoryOnly,
                    skipPayable != null && skipPayable, notes, imageUrl,
                    visitPeriodicity, visitDays);
        }

        List<PurchasingService.ContactInput> contactInputs() {
            return contacts == null ? List.of()
                    : contacts.stream().map(c -> new PurchasingService.ContactInput(
                            c.name(), c.role(), c.phone(), c.email())).toList();
        }
    }

    public record ContactRequest(@NotBlank String name, String role, String phone, String email) {
    }

    public record ImportSuppliersRequest(@NotEmpty List<SupplierRequest> suppliers) {
    }

    public record ReceivePurchaseRequest(
            @NotNull Long supplierId, @NotNull Long branchId, String invoiceRef,
            boolean onCredit, @NotEmpty List<LineRequest> lines) {
    }

    public record LineRequest(
            @NotNull Long productId, @NotNull BigDecimal quantity, @NotNull BigDecimal unitCost) {
    }

    public record PurchaseRequest(
            @NotNull Long supplierId, @NotNull Long branchId, String invoiceRef, String currency,
            BigDecimal exchangeRate, boolean onCredit, BigDecimal discount, BigDecimal discountPct,
            BigDecimal donation, BigDecimal retention, String cfdiUuid, String notes,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDate,
            @NotEmpty List<FullLineRequest> lines) {

        PurchasingService.PurchaseInput toInput() {
            List<PurchasingService.PurchaseLineFull> ls = lines.stream()
                    .map(l -> new PurchasingService.PurchaseLineFull(
                            l.productId(), l.description(), l.quantity(), l.unitCost(),
                            nz(l.discount()), nz(l.discountExtra()),
                            l.taxRate() == null ? MexicanTax.IVA_16 : l.taxRate(), l.expectedDate()))
                    .toList();
            return new PurchasingService.PurchaseInput(
                    supplierId, branchId, invoiceRef, currency == null ? "MXN" : currency,
                    exchangeRate == null ? BigDecimal.ONE : exchangeRate, onCredit,
                    nz(discount), nz(discountPct), nz(donation), nz(retention),
                    cfdiUuid, notes, expectedDate, ls);
        }
    }

    public record FullLineRequest(
            @NotNull Long productId, String description, @NotNull BigDecimal quantity,
            @NotNull BigDecimal unitCost, BigDecimal discount, BigDecimal discountExtra,
            BigDecimal taxRate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDate) {
    }

    public record PurchaseReturnRequest(
            Long purchaseId, Long supplierId, @NotNull Long branchId, String reason,
            @NotEmpty List<ReturnItemRequest> items) {
    }

    public record ReturnItemRequest(
            @NotNull Long productId, String description, @NotNull BigDecimal quantity,
            @NotNull BigDecimal unitCost) {
    }

    public record PayPayableRequest(@NotNull BigDecimal amount) {
    }

    public record VisitRoleRequest(@NotBlank String periodicity, String days) {
    }

    public record ScheduleVisitRequest(
            @NotNull Long supplierId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            String time, BigDecimal estimatedAmount, String notes) {
    }

    public record MarkVisitedRequest(BigDecimal purchaseAmount) {
    }

    public record CfdiParseRequest(@NotBlank String xml) {
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
