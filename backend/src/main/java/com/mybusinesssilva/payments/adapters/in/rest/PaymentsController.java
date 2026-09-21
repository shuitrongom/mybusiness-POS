package com.mybusinesssilva.payments.adapters.in.rest;

import com.mybusinesssilva.payments.application.PaymentsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mybusinesssilva.platform.security.AuthenticatedUser;

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
}
