package com.mybusinesssilva.bi.adapters.in.rest;

import com.mybusinesssilva.bi.application.BiService;
import com.mybusinesssilva.bi.domain.model.ProductRanking;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportación de reportes. Genera archivos descargables (CSV) a partir de los datos de BI.
 * CSV es abrible directamente en Excel. Requiere el módulo {@code bi} habilitado.
 */
@RestController
@RequestMapping("/api/v1/bi/export")
@PreAuthorize("@moduleAccess.canUse('bi')")
public class ReportExportController {

    private final BiService biService;

    public ReportExportController(BiService biService) {
        this.biService = biService;
    }

    /**
     * Exporta el ranking de productos (top por ingreso) como CSV descargable.
     */
    @GetMapping("/top-products")
    public ResponseEntity<byte[]> exportTopProducts(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "100") int limit) {
        List<ProductRanking> rows = biService.topProducts(days, limit);

        StringBuilder csv = new StringBuilder("Producto,Cantidad,Ingreso,Utilidad\n");
        for (ProductRanking r : rows) {
            csv.append(escape(r.name())).append(',')
                    .append(r.quantity()).append(',')
                    .append(r.revenue()).append(',')
                    .append(r.profit()).append('\n');
        }

        byte[] body = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"top-productos.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    /** Escapa un valor para CSV (comillas y comas). */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
