package com.mybusinesssilva.sales.adapters.in.rest;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.sales.application.CashRegisterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
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
 * Gestión de cajas registradoras por sucursal. La lectura la usa el cajero para elegir su caja
 * al abrir turno; el alta/activación requiere rol Dueño/Administrador con el módulo {@code cash}.
 */
@RestController
@RequestMapping("/api/v1/cash-registers")
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;

    public CashRegisterController(CashRegisterService cashRegisterService) {
        this.cashRegisterService = cashRegisterService;
    }

    @GetMapping
    @PreAuthorize("@moduleAccess.canUse('cash')")
    public List<Map<String, Object>> list(@RequestParam(value = "branchId", required = false) Long branchId) {
        return cashRegisterService.list(branchId);
    }

    @PostMapping
    @PreAuthorize("@moduleAccess.canManageCashRegisters()")
    public ResponseEntity<Map<String, Long>> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CashRegisterRequest request) {
        long id = cashRegisterService.create(actorEmail(actor), request.branchId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("cashRegisterId", id));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("@moduleAccess.canManageCashRegisters()")
    public ResponseEntity<Void> enable(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        cashRegisterService.setActive(actorEmail(actor), id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@moduleAccess.canManageCashRegisters()")
    public ResponseEntity<Void> disable(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        cashRegisterService.setActive(actorEmail(actor), id, false);
        return ResponseEntity.noContent().build();
    }

    private String actorEmail(AuthenticatedUser actor) {
        return actor == null ? "unknown" : actor.subject();
    }

    /** Alta de caja registradora. */
    public record CashRegisterRequest(@NotNull Long branchId, @NotBlank String name) {
    }
}
