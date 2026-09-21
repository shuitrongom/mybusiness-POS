package com.mybusinesssilva.licensing.adapters.in.rest;

import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
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
 * Panel del Super Admin: gestión de negocios (tenants), licencias y módulos.
 * Todos los endpoints requieren el rol SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/businesses")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminBusinessController {

    private final LicensingService licensingService;

    public SuperAdminBusinessController(LicensingService licensingService) {
        this.licensingService = licensingService;
    }

    @GetMapping
    public List<BusinessView> list() {
        return licensingService.listBusinesses().stream().map(BusinessView::from).toList();
    }

    @PostMapping
    public ResponseEntity<BusinessView> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateBusinessRequest request) {
        Business business = licensingService.createBusiness(
                actorEmail(actor), request.name(), request.rfc(),
                request.businessLine(), request.planId(), request.trialMonths());
        return ResponseEntity.status(HttpStatus.CREATED).body(BusinessView.from(business));
    }

    @PostMapping("/{id}/purchase")
    public ResponseEntity<Void> purchase(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        licensingService.purchaseLicense(actorEmail(actor), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/modules")
    public ResponseEntity<Void> sellModule(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id,
            @Valid @RequestBody SellModuleRequest request) {
        licensingService.sellSurchargeModule(actorEmail(actor), id, request.moduleKey(), request.price());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        licensingService.suspend(actorEmail(actor), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        licensingService.reactivate(actorEmail(actor), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/modules")
    public List<String> enabledModules(@PathVariable long id) {
        return licensingService.enabledModules(id);
    }

    private String actorEmail(AuthenticatedUser actor) {
        return actor == null ? "unknown" : actor.subject();
    }

    // --- DTOs ---

    /** Alta de negocio. */
    public record CreateBusinessRequest(
            @NotBlank String name,
            String rfc,
            @NotBlank String businessLine,
            @NotNull Long planId,
            @Min(0) int trialMonths) {
    }

    /** Venta de módulo adicional (excedente). */
    public record SellModuleRequest(
            @NotBlank String moduleKey,
            @NotNull BigDecimal price) {
    }

    /** Vista de negocio para la API. */
    public record BusinessView(
            Long id,
            String name,
            String rfc,
            String businessLine,
            String schemaName,
            String status,
            int trialMonths,
            String trialEndsAt,
            Long planId) {

        static BusinessView from(Business b) {
            return new BusinessView(
                    b.getId(), b.getName(), b.getRfc(), b.getBusinessLine(),
                    b.getSchemaName(), b.getStatus().name(), b.getTrialMonths(),
                    b.getTrialEndsAt() == null ? null : b.getTrialEndsAt().toString(),
                    b.getPlanId());
        }
    }
}
