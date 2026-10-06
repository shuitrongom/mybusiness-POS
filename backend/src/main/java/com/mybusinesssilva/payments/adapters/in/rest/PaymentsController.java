package com.mybusinesssilva.payments.adapters.in.rest;

import com.mybusinesssilva.payments.application.PaymentsService;
import com.mybusinesssilva.platform.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * Endpoints de recargas y pago de servicios. Requieren el módulo {@code payments} habilitado.
 *
 * <p>Si la operación falla en el agregador, se devuelve 422 (no se cobra al cliente).
 */
@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("@moduleAccess.canUse('payments')")
public class PaymentsController {

    private final PaymentsService paymentsService;

    public PaymentsController(PaymentsService paymentsService) {
        this.paymentsService = paymentsService;
    }

    @PostMapping("/recharge")
    public ResponseEntity<PaymentsService.OperationResult> recharge(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody RechargeRequest request) {
        PaymentsService.OperationResult result = paymentsService.sellRecharge(
                request.carrier(), request.phone(), request.amount(),
                request.branchId(), actor == null ? "unknown" : actor.subject());
        return respond(result);
    }

    @PostMapping("/service")
    public ResponseEntity<PaymentsService.OperationResult> service(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ServiceRequest request) {
        PaymentsService.OperationResult result = paymentsService.payService(
                request.biller(), request.reference(), request.amount(),
                request.branchId(), actor == null ? "unknown" : actor.subject());
        return respond(result);
    }

    @GetMapping("/commissions/total")
    public Map<String, BigDecimal> totalCommissions() {
        return Map.of("total", paymentsService.totalCommissions());
    }

    // ---- Saldo ----

    @GetMapping("/balance")
    public Map<String, Object> balance() {
        return paymentsService.balanceSummary();
    }

    // ---- Catálogo de compañías / servicios ----

    @GetMapping("/catalog")
    public List<Map<String, Object>> catalog(
            @RequestParam(value = "type", required = false) String type) {
        return paymentsService.listCatalog(type);
    }

    // ---- Operaciones realizadas ----

    @GetMapping("/operations")
    public List<Map<String, Object>> operations(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit) {
        return paymentsService.listOperations(type, from, to, limit);
    }

    // ---- Abonos / depósitos ----

    @GetMapping("/deposits")
    public List<Map<String, Object>> deposits(
            @RequestParam(value = "status", required = false) String status) {
        return paymentsService.listDeposits(status);
    }

    @PostMapping("/deposits")
    public ResponseEntity<Long> reportDeposit(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody DepositRequest r) {
        long id = paymentsService.reportDeposit(r.posId(), r.name(), r.email(), r.bank(),
                r.account(), r.reference(), r.amount(), r.payDate(), r.comments(),
                actor == null ? "unknown" : actor.subject());
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @PostMapping("/deposits/{id}/approve")
    public ResponseEntity<Map<String, BigDecimal>> approveDeposit(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        BigDecimal balance = paymentsService.approveDeposit(id, actor == null ? "unknown" : actor.subject());
        return ResponseEntity.ok(Map.of("balance", balance));
    }

    @PostMapping("/deposits/{id}/reject")
    public ResponseEntity<Void> rejectDeposit(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        paymentsService.rejectDeposit(id, actor == null ? "unknown" : actor.subject());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<PaymentsService.OperationResult> respond(
            PaymentsService.OperationResult result) {
        // Si falló en el agregador, se informa el error y no se considera cobro exitoso.
        return result.success()
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
    }

    /** Solicitud de recarga de tiempo aire. */
    public record RechargeRequest(
            @NotNull String carrier,
            @NotNull String phone,
            @NotNull @Positive BigDecimal amount,
            Long branchId) {
    }

    /** Solicitud de pago de servicio. */
    public record ServiceRequest(
            @NotNull String biller,
            @NotNull String reference,
            @NotNull @Positive BigDecimal amount,
            Long branchId) {
    }

    /** Reporte de un depósito bancario (abono al saldo). */
    public record DepositRequest(
            String posId,
            @NotBlank String name,
            String email,
            @NotBlank String bank,
            String account,
            @NotBlank String reference,
            @NotNull @Positive BigDecimal amount,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payDate,
            String comments) {
    }
}
