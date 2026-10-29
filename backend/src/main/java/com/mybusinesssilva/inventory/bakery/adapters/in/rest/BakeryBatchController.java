package com.mybusinesssilva.inventory.bakery.adapters.in.rest;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryBatch;
import com.mybusinesssilva.inventory.bakery.domain.model.BatchInput;
import com.mybusinesssilva.inventory.bakery.domain.model.BatchOutput;
import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryBatchUseCase;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BatchProductQueryPort;
import com.mybusinesssilva.inventory.rawmaterial.domain.port.out.RawMaterialRepository;
import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.platform.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
 * Endpoints de la TANDA del panadero y del panel admin de panadería.
 *
 * <p>
 * Barrera dura clave: la captura usa el sub-módulo {@code bakery_prod} (que el
 * PANADERO SÍ tiene), y el panel admin usa {@code bakery} (que el PANADERO NO
 * tiene).
 * El actor NUNCA viene por parámetro de cliente: se resuelve de
 * {@code actor.subject()}.
 */
@RestController
@RequestMapping("/api/v1/bakery/batches")
public class BakeryBatchController {

    private final BakeryBatchUseCase batches;
    private final RawMaterialRepository rawMaterialRepository;
    private final BatchProductQueryPort productQuery;

    public BakeryBatchController(BakeryBatchUseCase batches,
            RawMaterialRepository rawMaterialRepository,
            BatchProductQueryPort productQuery) {
        this.batches = batches;
        this.rawMaterialRepository = rawMaterialRepository;
        this.productQuery = productQuery;
    }

    // ---- Captura de la tanda (PANADERO/ADMIN/OWNER) ----

    @GetMapping("/raw-materials")
    @PreAuthorize("@moduleAccess.canUse('bakery_prod')")
    public List<Map<String, Object>> rawMaterials(@RequestParam("branchId") long branchId) {
        return rawMaterialRepository.listAvailableForBatch(branchId);
    }

    @GetMapping("/products")
    @PreAuthorize("@moduleAccess.canUse('bakery_prod')")
    public List<Map<String, Object>> products(@RequestParam("branchId") long branchId) {
        return productQuery.bakeryProductsWithPrice(branchId);
    }

    @PostMapping
    @PreAuthorize("@moduleAccess.can('bakery_prod','CREATE')")
    public ResponseEntity<Map<String, Object>> register(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody BatchRequest r) {
        List<BatchInput> inputs = r.inputs().stream()
                .map(i -> new BatchInput(i.rawMaterialId(), i.quantity(), null, null, null))
                .toList();
        List<BatchOutput> outputs = r.outputs().stream()
                .map(o -> new BatchOutput(o.productId(), o.quantity(), null, null, null))
                .toList();
        BakeryBatch batch = new BakeryBatch(
                null, null, r.branchId(), null, null, inputs, outputs,
                r.notes(), actorOf(actor), createdRole(actor));
        long batchId = batches.registerBatch(batch);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("batchId", batchId);
        body.put("folio", "T-" + batchId);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/mine")
    @PreAuthorize("@moduleAccess.can('bakery_prod','VIEW')")
    public List<Map<String, Object>> mine(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam("branchId") long branchId) {
        // El actor SIEMPRE del token: nunca por parámetro de cliente.
        return batches.myBatchesToday(branchId, actorOf(actor));
    }

    // ---- Panel admin (ADMIN/OWNER/SUPERVISOR) ----

    @GetMapping
    @PreAuthorize("@moduleAccess.can('bakery','VIEW')")
    public List<Map<String, Object>> list(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        return batches.listBatches(branchId, from, to, limit);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduleAccess.can('bakery','VIEW')")
    public Map<String, Object> detail(@PathVariable long id) {
        return batches.batchDetail(id);
    }

    @GetMapping("/yield")
    @PreAuthorize("@moduleAccess.can('bakery','VIEW')")
    public List<Map<String, Object>> yield(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return batches.yieldReport(branchId, from, to);
    }

    @GetMapping("/anomalies")
    @PreAuthorize("@moduleAccess.can('bakery','VIEW')")
    public List<Map<String, Object>> anomalies(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return batches.anomalies(branchId, from, to);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static String actorOf(AuthenticatedUser a) {
        return a == null ? "unknown" : a.subject();
    }

    /**
     * Resolvedor PROPIO que INCLUYE {@link Roles#PANADERO} (el helper de
     * {@code BakeryController} es private y devolvería null para un panadero).
     * Precedencia OWNER > ADMIN > SUPERVISOR > PANADERO > CASHIER.
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
        if (a.hasRole(Roles.PANADERO)) {
            return Roles.PANADERO;
        }
        if (a.hasRole(Roles.CASHIER)) {
            return Roles.CASHIER;
        }
        return null;
    }

    // =====================================================================
    // DTOs
    // =====================================================================

    public record BatchRequest(
            @NotNull Long branchId,
            String notes,
            @NotEmpty List<InputLine> inputs,
            @NotEmpty List<OutputLine> outputs) {
    }

    public record InputLine(
            @NotNull Long rawMaterialId,
            @NotNull @Positive @DecimalMax("999999999.999") BigDecimal quantity) {
    }

    public record OutputLine(
            @NotNull Long productId,
            @NotNull @Positive @DecimalMax("999999999.999") BigDecimal quantity) {
    }
}
