package com.mybusinesssilva.sales.adapters.in.rest;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.sales.application.DocumentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Bitácora de comprobantes impresos: registro (evidencia) y consulta clasificada para el dueño.
 */
@RestController
@RequestMapping("/api/v1/documents")
@PreAuthorize("@moduleAccess.canUse('cash')")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** Registra la evidencia de un comprobante impreso (lo llama el POS/cajero al imprimir). */
    @PostMapping
    public Map<String, Long> record(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @RequestBody RecordRequest r) {
        long id = documentService.record(r.docType(), r.folio(), r.title(), r.shiftId(), r.branchId(),
                r.amount(), actor == null ? "unknown" : actor.subject(), r.reprint(), r.payload());
        return Map.of("id", id);
    }

    /** Lista comprobantes por tipo/cajero/fecha (para el dueño/administrador). */
    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String cashier,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        return documentService.list(type, cashier, from, to, limit);
    }

    /** Detalle (payload) de un comprobante para reimprimirlo. */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return documentService.detail(id);
    }

    /** Resumen por tipo de documento (panel del dueño). */
    @GetMapping("/summary")
    public List<Map<String, Object>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return documentService.summaryByType(from, to);
    }

    public record RecordRequest(String docType, String folio, String title, Long shiftId,
                                Long branchId, BigDecimal amount, boolean reprint,
                                Map<String, Object> payload) {
    }
}
