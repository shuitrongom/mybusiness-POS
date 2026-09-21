package com.mybusinesssilva.invoicing.adapters.in.rest;

import com.mybusinesssilva.invoicing.application.InvoicingService;
import com.mybusinesssilva.invoicing.domain.model.CfdiConcept;
import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de facturación CFDI 4.0. Requieren el módulo {@code invoicing} habilitado.
 */
@RestController
@RequestMapping("/api/v1/invoicing")
@PreAuthorize("@moduleAccess.canUse('invoicing')")
public class InvoicingController {

    private final InvoicingService invoicingService;

    public InvoicingController(InvoicingService invoicingService) {
        this.invoicingService = invoicingService;
    }

    @PostMapping("/invoices")
    public ResponseEntity<InvoicingService.InvoiceResult> issue(
            @Valid @RequestBody IssueInvoiceRequest request) {
        ReceiverInfo receiver = new ReceiverInfo(
                request.receiverRfc(), request.receiverName(), request.receiverZip(),
                request.receiverRegime(), request.cfdiUse());
        List<CfdiConcept> concepts = request.concepts().stream()
                .map(c -> new CfdiConcept(c.satProdServ(), c.satUnit(), c.description(),
                        c.quantity(), c.unitPrice(), c.amount()))
                .toList();
        return ResponseEntity.ok(invoicingService.issueInvoice(
                request.saleId(), receiver, concepts, request.idempotencyKey()));
    }

    @PostMapping("/invoices/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable long id) {
        boolean canceled = invoicingService.cancelInvoice(id);
        return canceled ? ResponseEntity.noContent().build() : ResponseEntity.unprocessableEntity().build();
    }

    /** Registra un complemento de pago sobre una factura a crédito timbrada. */
    @PostMapping("/invoices/{id}/payment-complement")
    public ResponseEntity<java.util.Map<String, Long>> paymentComplement(
            @PathVariable long id, @Valid @RequestBody PaymentComplementRequest request) {
        long complementId = invoicingService.registerPaymentComplement(
                id, request.paidAmount(), request.paymentForm(), request.paymentDate());
        return ResponseEntity.ok(java.util.Map.of("complementId", complementId));
    }

    /** Emite la factura global (agrupado del público en general). */
    @PostMapping("/global")
    public InvoicingService.InvoiceResult global(@Valid @RequestBody GlobalRequest request) {
        return invoicingService.issueGlobalInvoice(request.totalAmount(), request.description());
    }

    /** Complemento de pago. */
    public record PaymentComplementRequest(
            @NotNull BigDecimal paidAmount,
            @NotNull String paymentForm,
            @NotNull String paymentDate) {
    }

    /** Factura global. */
    public record GlobalRequest(
            @NotNull BigDecimal totalAmount,
            String description) {
    }

    /** Solicitud de emisión de factura. */
    public record IssueInvoiceRequest(
            Long saleId,
            @NotNull String receiverRfc,
            @NotNull String receiverName,
            @NotNull String receiverZip,
            @NotNull String receiverRegime,
            @NotNull String cfdiUse,
            @NotEmpty List<ConceptRequest> concepts,
            String idempotencyKey) {
    }

    /** Concepto de la factura. */
    public record ConceptRequest(
            @NotNull String satProdServ,
            String satUnit,
            String description,
            @NotNull BigDecimal quantity,
            @NotNull BigDecimal unitPrice,
            @NotNull BigDecimal amount) {
    }
}
