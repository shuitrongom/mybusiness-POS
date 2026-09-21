package com.mybusinesssilva.invoicing.adapters.in.rest;

import com.mybusinesssilva.invoicing.application.InvoicingService;
import com.mybusinesssilva.invoicing.domain.model.CfdiConcept;
import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal público de autofacturación: permite que el propio cliente genere la factura de su ticket
 * con sus datos fiscales, sin necesidad de iniciar sesión. Es un endpoint público (ver SecurityConfig).
 *
 * <p>El negocio se identifica por el encabezado de tenant (subdominio del portal del negocio).
 * El cliente indica el folio/venta y captura sus datos fiscales.
 */
@RestController
@RequestMapping("/api/v1/self-invoice")
public class SelfInvoiceController {

    private final InvoicingService invoicingService;

    public SelfInvoiceController(InvoicingService invoicingService) {
        this.invoicingService = invoicingService;
    }

    @PostMapping
    public InvoicingService.InvoiceResult selfInvoice(
            @RequestHeader("X-Tenant-Id") String tenant,
            @Valid @RequestBody SelfInvoiceRequest request) {
        // El tenant del portal público se fija manualmente (no viene de un JWT).
        TenantContext.setTenantId(tenant.startsWith("tenant_") ? tenant : "tenant_" + tenant);
        try {
            ReceiverInfo receiver = new ReceiverInfo(
                    request.receiverRfc(), request.receiverName(), request.receiverZip(),
                    request.receiverRegime(), request.cfdiUse());
            List<CfdiConcept> concepts = request.concepts().stream()
                    .map(c -> new CfdiConcept(c.satProdServ(), c.satUnit(), c.description(),
                            c.quantity(), c.unitPrice(), c.amount()))
                    .toList();
            return invoicingService.selfInvoice(
                    request.saleId(), receiver, concepts, request.idempotencyKey());
        } finally {
            TenantContext.clear();
        }
    }

    /** Solicitud de autofacturación desde el portal público. */
    public record SelfInvoiceRequest(
            @NotNull Long saleId,
            @NotNull String receiverRfc,
            @NotNull String receiverName,
            @NotNull String receiverZip,
            @NotNull String receiverRegime,
            @NotNull String cfdiUse,
            @NotEmpty List<ConceptRequest> concepts,
            String idempotencyKey) {
    }

    /** Concepto de la autofactura. */
    public record ConceptRequest(
            @NotNull String satProdServ,
            String satUnit,
            String description,
            @NotNull BigDecimal quantity,
            @NotNull BigDecimal unitPrice,
            @NotNull BigDecimal amount) {
    }
}
