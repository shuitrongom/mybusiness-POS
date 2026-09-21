package com.mybusinesssilva.platform.billing;

import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mybusinesssilva.platform.security.AuthenticatedUser;

/**
 * Registro de ventas del Super Admin (licencias y módulos adicionales) con su facturación
 * (CFDI si el cliente lo requiere, o comprobante PDF al correo). Requiere rol SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/sales")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminSaleController {

    private final SuperAdminBillingService billingService;

    public SuperAdminSaleController(SuperAdminBillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping
    public ResponseEntity<SuperAdminBillingService.SaleReceipt> registerSale(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody RegisterSaleRequest request) {

        ReceiverInfo receiver = request.requiresInvoice()
                ? new ReceiverInfo(request.receiverRfc(), request.receiverName(),
                        request.receiverZip(), request.receiverRegime(), request.cfdiUse())
                : null;

        var receipt = billingService.registerSale(
                actor == null ? "unknown" : actor.subject(),
                request.businessId(), request.kind(), request.amount(),
                request.requiresInvoice(), request.customerEmail(), receiver, request.concept());

        return ResponseEntity.ok(receipt);
    }

    /** Solicitud de registro de venta del Super Admin. */
    public record RegisterSaleRequest(
            @NotNull Long businessId,
            @NotNull String kind,          // LICENSE o SURCHARGE
            @NotNull BigDecimal amount,
            boolean requiresInvoice,
            String customerEmail,
            String concept,
            // Datos fiscales (requeridos solo si requiresInvoice = true):
            String receiverRfc,
            String receiverName,
            String receiverZip,
            String receiverRegime,
            String cfdiUse) {
    }
}
