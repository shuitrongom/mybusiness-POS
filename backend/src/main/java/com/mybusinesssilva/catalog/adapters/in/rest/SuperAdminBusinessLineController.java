package com.mybusinesssilva.catalog.adapters.in.rest;

import com.mybusinesssilva.catalog.application.BusinessLineService;
import com.mybusinesssilva.catalog.domain.model.BusinessLine;
import com.mybusinesssilva.platform.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel del Super Admin: gestión de giros (líneas de negocio). Solo rol SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/business-lines")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminBusinessLineController {

    private final BusinessLineService service;

    public SuperAdminBusinessLineController(BusinessLineService service) {
        this.service = service;
    }

    @GetMapping
    public List<BusinessLineView> list() {
        return service.list().stream().map(BusinessLineView::from).toList();
    }

    @PostMapping
    public ResponseEntity<BusinessLineView> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody UpsertBusinessLineRequest request) {
        BusinessLine line = service.create(
                actorEmail(actor), request.code(), request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(BusinessLineView.from(line));
    }

    @PutMapping("/{code}")
    public BusinessLineView update(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable String code,
            @Valid @RequestBody UpsertBusinessLineRequest request) {
        BusinessLine line = service.update(
                actorEmail(actor), code, request.name(), request.description());
        return BusinessLineView.from(line);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable String code) {
        service.delete(actorEmail(actor), code);
        return ResponseEntity.noContent().build();
    }

    private String actorEmail(AuthenticatedUser actor) {
        return actor == null ? "unknown" : actor.subject();
    }

    /** Alta/edición de giro. El código es opcional al crear (se deriva del nombre). */
    public record UpsertBusinessLineRequest(
            String code,
            @NotBlank String name,
            String description) {
    }

    /** Vista de giro para la API. */
    public record BusinessLineView(String code, String name, String description) {
        static BusinessLineView from(BusinessLine l) {
            return new BusinessLineView(l.code(), l.name(), l.description());
        }
    }
}
