package com.mybusinesssilva.invoicing.adapters.in.rest;

import com.mybusinesssilva.invoicing.application.IssuerService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Datos fiscales del emisor y series/folios (pantalla "Datos para factura Electrónica").
 */
@RestController
@RequestMapping("/api/v1/invoicing/issuer")
@PreAuthorize("@moduleAccess.canUse('invoicing')")
public class IssuerController {

    private final IssuerService issuerService;

    public IssuerController(IssuerService issuerService) {
        this.issuerService = issuerService;
    }

    @GetMapping
    public Map<String, Object> get() {
        return issuerService.getIssuer();
    }

    @PutMapping
    public Map<String, String> save(@RequestBody IssuerService.IssuerData data) {
        issuerService.saveIssuer(data);
        return Map.of("status", "ok");
    }

    @GetMapping("/series")
    public List<Map<String, Object>> series() {
        return issuerService.listSeries();
    }

    @PostMapping("/series")
    public Map<String, String> saveSeries(@RequestBody SeriesRequest r) {
        issuerService.saveSeries(r.docType(), r.series(), r.lastFolio());
        return Map.of("status", "ok");
    }

    public record SeriesRequest(String docType, String series, long lastFolio) {
    }
}
