package com.mybusinesssilva.sales.adapters.in.rest;

import com.mybusinesssilva.sales.application.ShiftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
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
        // Abrir caja es operar caja: lo hace el personal operativo (cajeros), NO administración ni
        // supervisión. Dueño/Admin administran, Supervisor supervisa; ninguno abre caja ni vende.
        boolean adminLike = actor != null
                && (actor.hasRole("OWNER") || actor.hasRole("ADMIN") || actor.hasRole("SUPERVISOR"));
        if (adminLike) {
            throw new IllegalStateException(
                    "Tu rol no opera caja. La apertura de caja y las ventas son del personal de caja (cajeros).");
        }
        // El bloqueo de "ya cerró hoy" aplica al personal de caja (que es quien abre).
        long shiftId = shiftService.openShift(
                request.cashRegisterId(),
                actor == null ? "unknown" : actor.subject(),
                request.openingFloat(),
                request.branchId(),
                request.handoverFrom(),
                request.notes(),
                true);
        return ResponseEntity.ok(Map.of("shiftId", shiftId));
    }

    /**
     * Estado del día del cajero: si ya cerró su caja hoy, el POS lo bloquea con un mensaje claro
     * ("tu corte ya se cerró, vuelve mañana"). No aplica a Dueño/Admin/Supervisor.
     */
    @GetMapping("/day-status")
    public Map<String, Object> dayStatus(@AuthenticationPrincipal AuthenticatedUser actor) {
        boolean isCashier = actor != null && actor.hasRole("CASHIER")
                && !actor.hasRole("OWNER") && !actor.hasRole("ADMIN") && !actor.hasRole("SUPERVISOR");
        boolean closedToday = isCashier && shiftService.cashierClosedToday(actor.subject());
        return Map.of("closedToday", closedToday);
    }

    /** Turno abierto del cajero actual (para que el POS decida si pedir apertura de caja). */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> active(@AuthenticationPrincipal AuthenticatedUser actor) {
        return shiftService.activeShiftOf(actor == null ? "unknown" : actor.subject())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Resumen en vivo del turno de hoy del cajero (su corte parcial). 204 si no tiene caja abierta. */
    @GetMapping("/my-summary")
    public ResponseEntity<Map<String, Object>> mySummary(@AuthenticationPrincipal AuthenticatedUser actor) {
        return shiftService.myShiftSummary(actor == null ? "unknown" : actor.subject())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Reporte de ventas por cajero (para el admin). */
    @GetMapping("/sales-by-cashier")
    public java.util.List<Map<String, Object>> salesByCashier(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return shiftService.salesByCashier(from, to);
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

    /** Corte X: lectura parcial del turno sin cerrarlo. */
    @PostMapping("/{id}/cut-x")
    public ShiftService.ShiftClosure cutX(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id) {
        return shiftService.partialCut(id, actor == null ? "unknown" : actor.subject());
    }

    /** Historial de cortes (X y Z) para consulta o reimpresión. */
    @GetMapping("/cuts")
    public java.util.List<Map<String, Object>> cuts(
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        return shiftService.listCuts(limit);
    }

    public record OpenShiftRequest(@NotNull Long cashRegisterId, BigDecimal openingFloat,
                                   Long branchId, Long handoverFrom, String notes) {
    }

    public record CashMovementRequest(
            @NotNull String direction, @NotNull BigDecimal amount, String reason) {
    }

    public record CloseShiftRequest(@NotNull BigDecimal countedCash) {
    }
}
