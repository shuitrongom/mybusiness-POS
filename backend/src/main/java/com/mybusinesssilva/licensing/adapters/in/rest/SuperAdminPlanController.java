package com.mybusinesssilva.licensing.adapters.in.rest;

import com.mybusinesssilva.licensing.application.PlanService;
import com.mybusinesssilva.licensing.domain.model.Plan;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mybusinesssilva.platform.security.AuthenticatedUser;

/**
 * Panel del Super Admin: catálogo de planes y su gestión (crear, editar, duplicar, desactivar),
 * sin necesidad de programar. Todos los endpoints requieren el rol SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/plans")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminPlanController {

    private final PlanService planService;

    public SuperAdminPlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public List<Plan> listActivePlans() {
        return planService.listActive();
    }

    @PostMapping
    public ResponseEntity<Plan> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody PlanRequest request) {
        Plan plan = planService.create(email(actor), request.code(), request.name(),
                request.description(), request.licensePrice(), request.moduleKeys());
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @PutMapping("/{id}")
    public Plan update(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id,
            @Valid @RequestBody UpdatePlanRequest request) {
        return planService.update(email(actor), id, request.name(), request.description(),
                request.licensePrice(), request.active(), request.moduleKeys());
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<Plan> duplicate(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id,
            @Valid @RequestBody DuplicateRequest request) {
        Plan plan = planService.duplicate(email(actor), id, request.newCode(), request.newName());
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        planService.deactivate(email(actor), id);
        return ResponseEntity.noContent().build();
    }

    private String email(AuthenticatedUser actor) {
        return actor == null ? "unknown" : actor.subject();
    }

    /** Alta de plan. */
    public record PlanRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotNull BigDecimal licensePrice,
            List<String> moduleKeys) {
    }

    /** Edición de plan. */
    public record UpdatePlanRequest(
            @NotBlank String name,
            String description,
            @NotNull BigDecimal licensePrice,
            boolean active,
            List<String> moduleKeys) {
    }

    /** Duplicado de plan. */
    public record DuplicateRequest(
            @NotBlank String newCode,
            @NotBlank String newName) {
    }
}
