package com.mybusinesssilva.invoicing.adapters.in.rest;

import com.mybusinesssilva.invoicing.application.InvoicingService;
import com.mybusinesssilva.invoicing.application.RemissionService;
import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversión de remisiones y tickets a factura, y factura de cierre (global por periodo).
 * Pantallas "Remisiones a Factura", "Tickets a Factura" y "Factura de Cierre".
 */
@RestController
@RequestMapping("/api/v1/invoicing")
@PreAuthorize("@moduleAccess.canUse('invoicing')")
public class RemissionController {

    private final RemissionService remissionService;

    public RemissionController(RemissionService remissionService) {
        this.remissionService = remissionService;
    }

    @GetMapping("/remissions")
    public List<Map<String, Object>> remissions() {
        return remissionService.openRemissions();
    }

    @PostMapping("/remissions/{id}/to-invoice")
    public InvoicingService.InvoiceResult remissionToInvoice(
            @org.springframework.web.bind.annotation.PathVariable long id,
            @RequestBody ReceiverRequest r) {
        return remissionService.remissionToInvoice(id, r.toReceiver());
    }

    @GetMapping("/uninvoiced-sales")
    public List<Map<String, Object>> uninvoicedSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return remissionService.uninvoicedSales(from, to);
    }

    @PostMapping("/sales/{id}/to-invoice")
    public InvoicingService.InvoiceResult ticketToInvoice(
            @org.springframework.web.bind.annotation.PathVariable long id,
            @RequestBody ReceiverRequest r) {
        return remissionService.ticketToInvoice(id, r.toReceiver());
    }

    @PostMapping("/closing")
    public RemissionService.ClosingResult closing(@RequestBody ClosingRequest r) {
        return remissionService.closingInvoice(r.from(), r.to());
    }

    public record ReceiverRequest(String receiverRfc, String receiverName, String receiverZip,
                                  String receiverRegime, String cfdiUse) {
        ReceiverInfo toReceiver() {
            return new ReceiverInfo(receiverRfc, receiverName, receiverZip, receiverRegime, cfdiUse);
        }
    }

    public record ClosingRequest(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    }
}
