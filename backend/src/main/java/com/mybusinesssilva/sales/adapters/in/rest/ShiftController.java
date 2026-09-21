package com.mybusinesssilva.sales.adapters.in.rest;

import com.mybusinesssilva.sales.application.ShiftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mybusinesssilva.platform.security.AuthenticatedUser;

/**
 * Endpoints de turnos y cortes de caja. Requieren el módulo {@code cash} habilitado.
 */
@RestController
@RequestMapping("/api/v1/shifts")
@PreAuthorize("@moduleAccess.canUse('cash')")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @PostMapping("/open")
    public ResponseEntity<Map<String, Long>> open(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody OpenShiftRequest request) {
        long shiftId = shiftService.openShift(
                request.cashRegisterId(),
                actor == null ? "unknown" : actor.subject(),
                request.openingFloat());
        return ResponseEntity.ok(Map.of("shiftId", shiftId));
    }

    @PostMapping("/{id}/cash-movement")
    public ResponseEntity<Void> cashMovement(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id,
            @Valid @RequestBody CashMovementRequest request) {
        shiftService.recordCashMovement(id, request.direction(), request.amount(),
                request.reason(), actor == null ? "unknown" : actor.subject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/close")
    public ShiftService.ShiftClosure close(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id,
            @Valid @RequestBody CloseShiftRequest request) {
        return shiftService.closeShift(id,
                actor == null ? "unknown" : actor.subject(), request.countedCash());
    }

    public record OpenShiftRequest(@NotNull Long cashRegisterId, BigDecimal openingFloat) {
    }

    public record CashMovementRequest(
            @NotNull String direction, @NotNull BigDecimal amount, String reason) {
    }

    public record CloseShiftRequest(@NotNull BigDecimal countedCash) {
    }
}
