package com.mybusinesssilva.invoicing.adapters.in.rest;

import com.mybusinesssilva.invoicing.application.SatCatalogService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogos SAT (lectura) y asignación de claves a productos y líneas. Pantallas "Catálogo SAT
 * Productos" y "Catálogo SAT Líneas".
 */
@RestController
@RequestMapping("/api/v1/invoicing/sat")
@PreAuthorize("@moduleAccess.canUse('invoicing')")
public class SatCatalogController {

    private final SatCatalogService satService;

    public SatCatalogController(SatCatalogService satService) {
        this.satService = satService;
    }

    @GetMapping("/prod-serv")
    public List<Map<String, Object>> prodServ(@RequestParam(required = false) String q) {
        return satService.searchProdServ(q);
    }

    @GetMapping("/unit")
    public List<Map<String, Object>> unit(@RequestParam(required = false) String q) {
        return satService.searchUnit(q);
    }

    @GetMapping("/regime")
    public List<Map<String, Object>> regime() {
        return satService.regimes();
    }

    @GetMapping("/uso-cfdi")
    public List<Map<String, Object>> usoCfdi() {
        return satService.usosCfdi();
    }

    @GetMapping("/forma-pago")
    public List<Map<String, Object>> formaPago() {
        return satService.formasPago();
    }

    @GetMapping("/metodo-pago")
    public List<Map<String, Object>> metodoPago() {
        return satService.metodosPago();
    }

    @GetMapping("/moneda")
    public List<Map<String, Object>> moneda() {
        return satService.monedas();
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products(@RequestParam(required = false) String q) {
        return satService.productsWithSat(q);
    }

    @PostMapping("/products/{id}")
    public Map<String, String> assignProduct(@PathVariable long id, @RequestBody AssignProductRequest r) {
        satService.assignToProduct(id, r.satProdServ(), r.satUnit(), r.taxObject(), r.retIva(), r.retIsr());
        return Map.of("status", "ok");
    }

    @GetMapping("/lines")
    public List<Map<String, Object>> lines() {
        return satService.linesWithSat();
    }

    @PostMapping("/lines")
    public Map<String, Object> assignLine(@RequestBody AssignLineRequest r) {
        int updated = satService.assignToLine(r.lineName(), r.satProdServ(), r.satUnit(), r.taxObject());
        return Map.of("status", "ok", "productsUpdated", updated);
    }

    public record AssignProductRequest(String satProdServ, String satUnit, String taxObject,
                                       BigDecimal retIva, BigDecimal retIsr) {
    }

    public record AssignLineRequest(String lineName, String satProdServ, String satUnit, String taxObject) {
    }
}
