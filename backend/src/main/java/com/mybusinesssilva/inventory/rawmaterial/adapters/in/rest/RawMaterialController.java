package com.mybusinesssilva.inventory.rawmaterial.adapters.in.rest;

import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterial;
import com.mybusinesssilva.inventory.rawmaterial.domain.port.in.RawMaterialUseCase;
import com.mybusinesssilva.platform.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
 * Catálogo y kardex de materia prima. La MP es administración de inventario,
 * reservada a OWNER/ADMIN/SUPERVISOR: nunca cajero ni panadero para altas. El
 * panadero lee los insumos para la tanda por el módulo {@code bakery_prod}
 * (ver {@code BakeryBatchController}), no por aquí.
 */
@RestController
@RequestMapping("/api/v1/raw-materials")
@PreAuthorize("@moduleAccess.canUse('inventory')")
public class RawMaterialController {

    private final RawMaterialUseCase rawMaterials;

    public RawMaterialController(RawMaterialUseCase rawMaterials) {
        this.rawMaterials = rawMaterials;
    }

    @GetMapping
    @PreAuthorize("@moduleAccess.can('inventory','VIEW')")
    public List<Map<String, Object>> list(
            @RequestParam(value = "branchId", required = false) Long branchId) {
        return rawMaterials.list(branchId);
    }

    @PostMapping
    @PreAuthorize("@moduleAccess.can('inventory','CREATE')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody RawMaterialRequest r) {
        long id = rawMaterials.create(toDomain(null, r));
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("id", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@moduleAccess.can('inventory','EDIT')")
    public ResponseEntity<Void> update(@PathVariable long id, @Valid @RequestBody RawMaterialRequest r) {
        rawMaterials.update(id, toDomain(id, r));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/entries")
    @PreAuthorize("@moduleAccess.can('inventory','CREATE')")
    public ResponseEntity<Void> registerEntry(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id,
            @Valid @RequestBody EntryRequest r) {
        rawMaterials.registerEntry(id, r.branchId(), r.quantity(), r.unitCost(), actorOf(actor));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/active")
    @PreAuthorize("@moduleAccess.can('inventory','EDIT')")
    public ResponseEntity<Void> setActive(@PathVariable long id, @Valid @RequestBody ActiveRequest r) {
        rawMaterials.setActive(id, r.active());
        return ResponseEntity.noContent().build();
    }

    /**
     * Borrado físico de un insumo VIRGEN (creado por error, sin existencia ni movimientos).
     * Misma acción {@code EDIT} que las demás mutaciones no-CREATE del módulo. Si el insumo ya
     * se usó, el service responde 409 ({@code RAW_MATERIAL_IN_USE}) y el front ofrece desactivar.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@moduleAccess.can('inventory','EDIT')")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        rawMaterials.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize("@moduleAccess.can('inventory','VIEW')")
    public List<Map<String, Object>> movements(
            @PathVariable long id,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return rawMaterials.movements(id, from, to);
    }

    private static String actorOf(AuthenticatedUser a) {
        return a == null ? "unknown" : a.subject();
    }

    private static RawMaterial toDomain(Long id, RawMaterialRequest r) {
        return new RawMaterial(
                id,
                r.sku() == null || r.sku().isBlank() ? null : r.sku().trim(),
                r.name().trim(),
                r.unit(),
                r.unitCost() == null ? BigDecimal.ZERO : r.unitCost(),
                r.minQuantity() == null ? BigDecimal.ZERO : r.minQuantity(),
                true);
    }

    // =====================================================================
    // DTOs
    // =====================================================================

    public record RawMaterialRequest(
            @Size(max = 60) String sku,
            @NotBlank @Size(max = 160) String name,
            @NotBlank String unit,
            @PositiveOrZero @DecimalMax("9999999999.9999") BigDecimal unitCost,
            @PositiveOrZero @DecimalMax("999999999.999") BigDecimal minQuantity) {
    }

    public record EntryRequest(
            @NotNull Long branchId,
            @NotNull @Positive @DecimalMax("999999999.999") BigDecimal quantity,
            @PositiveOrZero @DecimalMax("9999999999.9999") BigDecimal unitCost) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }
}
