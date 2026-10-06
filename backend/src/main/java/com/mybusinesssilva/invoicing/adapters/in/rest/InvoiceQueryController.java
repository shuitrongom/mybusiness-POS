package com.mybusinesssilva.invoicing.adapters.in.rest;

import com.mybusinesssilva.invoicing.application.InvoiceQueryService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas de comprobantes: lista de facturas, lista de recibos de pago, documentos pendientes de
 * pago y conceptos de un CFDI.
 */
@RestController
@RequestMapping("/api/v1/invoicing")
@PreAuthorize("@moduleAccess.canUse('invoicing')")
public class InvoiceQueryController {

    private final InvoiceQueryService queryService;

    public InvoiceQueryController(InvoiceQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/invoices")
    public List<Map<String, Object>> invoices(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String client,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        return queryService.listInvoices(kind, from, to, client, limit);
    }

    @GetMapping("/invoices/{id}/concepts")
    public List<Map<String, Object>> concepts(@PathVariable long id) {
        return queryService.conceptsOf(id);
    }

    @GetMapping("/payments")
    public List<Map<String, Object>> payments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String client,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        return queryService.listPayments(from, to, client, limit);
    }

    @GetMapping("/pending-for-payment")
    public List<Map<String, Object>> pending(@RequestParam(required = false) String client) {
        return queryService.pendingForPayment(client);
    }
}
