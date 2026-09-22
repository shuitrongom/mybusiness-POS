package com.mybusinesssilva.platform.branches;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
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

/**
 * Gestión de sucursales del negocio. La lectura es accesible a cualquier usuario operativo
 * (para elegir sucursal en el POS); el alta/edición/activación requiere el módulo
 * {@code multibranch} y rol Dueño/Administrador.
 */
@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    @PreAuthorize("@moduleAccess.canReadBranches()")
    public List<Map<String, Object>> list() {
        return branchService.list();
    }

    @PostMapping
    @PreAuthorize("@moduleAccess.canManageBranches()")
    public ResponseEntity<Map<String, Long>> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody BranchRequest request) {
        long id = branchService.create(actorEmail(actor), request.name(), request.code());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("branchId", id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@moduleAccess.canManageBranches()")
    public ResponseEntity<Void> update(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id,
            @Valid @RequestBody BranchRequest request) {
        branchService.update(actorEmail(actor), id, request.name(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("@moduleAccess.canManageBranches()")
    public ResponseEntity<Void> enable(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        branchService.setActive(actorEmail(actor), id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@moduleAccess.canManageBranches()")
    public ResponseEntity<Void> disable(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        branchService.setActive(actorEmail(actor), id, false);
        return ResponseEntity.noContent().build();
    }

    private String actorEmail(AuthenticatedUser actor) {
        return actor == null ? "unknown" : actor.subject();
    }

    /** Alta/edición de sucursal. */
    public record BranchRequest(@NotBlank String name, String code) {
    }
}
