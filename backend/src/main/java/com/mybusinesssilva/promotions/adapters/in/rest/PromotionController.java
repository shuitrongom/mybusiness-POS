package com.mybusinesssilva.promotions.adapters.in.rest;

import com.mybusinesssilva.promotions.application.PromotionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de PROMOCIONES.
 *
 * <p>La lectura de promociones vigentes es accesible para vender (el punto de venta las aplica
 * automáticamente al carrito). La gestión (alta/edición/baja) queda reservada a quien puede
 * administrar la configuración del negocio (Dueño/Administrador).
 */
@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    /** Promociones vigentes hoy (para aplicar en el punto de venta). */
    @GetMapping("/active")
    @PreAuthorize("@moduleAccess.canUse('sales')")
    public List<Map<String, Object>> active() {
        return promotionService.listActive();
    }

    /** Todas las promociones (gestor). */
    @GetMapping
    @PreAuthorize("@moduleAccess.canManageSettings()")
    public List<Map<String, Object>> list() {
        return promotionService.listAll();
    }

    @PostMapping
    @PreAuthorize("@moduleAccess.canManageSettings()")
    public ResponseEntity<Long> create(@Valid @RequestBody PromotionRequest request) {
        long id = promotionService.create(request.toInput());
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@moduleAccess.canManageSettings()")
    public ResponseEntity<Void> update(@PathVariable long id, @Valid @RequestBody PromotionRequest request) {
        promotionService.update(id, request.toInput());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@moduleAccess.canManageSettings()")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        promotionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Alta/edición de promoción. */
    public record PromotionRequest(
            @NotBlank String name,
            @NotBlank String type,
            String scope,
            Long categoryId,
            BigDecimal value,
            BigDecimal buyQty,
            BigDecimal payQty,
            BigDecimal minAmount,
            LocalDate startsOn,
            LocalDate endsOn,
            Integer maxUses,
            Integer priority,
            Boolean active,
            List<Long> productIds) {

        PromotionService.PromotionInput toInput() {
            return new PromotionService.PromotionInput(
                    name, type, scope == null || scope.isBlank() ? "PRODUCT" : scope, categoryId,
                    value == null ? BigDecimal.ZERO : value,
                    buyQty == null ? BigDecimal.ZERO : buyQty,
                    payQty == null ? BigDecimal.ZERO : payQty,
                    minAmount == null ? BigDecimal.ZERO : minAmount,
                    startsOn, endsOn, maxUses,
                    priority == null ? 0 : priority,
                    active == null || active,
                    productIds == null ? List.of() : productIds);
        }
    }
}
