package com.mybusinesssilva.invoicing.adapters.in.rest;

import com.mybusinesssilva.invoicing.application.PaymentReceiptService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generación de recibos electrónicos de pago (complemento de Pagos 2.0). Pantalla "Generar recibos
 * de pago".
 */
@RestController
@RequestMapping("/api/v1/invoicing/payment-receipts")
@PreAuthorize("@moduleAccess.canUse('invoicing')")
public class PaymentReceiptController {

    private final PaymentReceiptService receiptService;

    public PaymentReceiptController(PaymentReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PostMapping
    public PaymentReceiptService.ReceiptResult generate(@RequestBody GenerateRequest r) {
        List<PaymentReceiptService.AppliedDoc> docs = r.docs().stream()
                .map(d -> new PaymentReceiptService.AppliedDoc(d.cfdiId(), d.installment(), d.paidAmount()))
                .toList();
        return receiptService.generate(r.customerId(), r.paymentForm(), r.paymentDate(),
                r.bank(), r.operationNo(), docs);
    }

    public record GenerateRequest(
            Long customerId,
            @NotNull String paymentForm,
            @NotNull String paymentDate,
            String bank,
            String operationNo,
            @NotEmpty List<DocRequest> docs) {
    }

    public record DocRequest(@NotNull Long cfdiId, int installment, @NotNull BigDecimal paidAmount) {
    }
}
