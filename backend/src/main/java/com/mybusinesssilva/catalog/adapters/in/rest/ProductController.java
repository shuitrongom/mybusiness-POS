package com.mybusinesssilva.catalog.adapters.in.rest;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del catálogo de productos del negocio.
 *
 * <p>Requieren que el negocio tenga habilitado el módulo de inventario (regla de tres capas
 * de autorización: módulo habilitado comercialmente Y permiso del rol).
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
@PreAuthorize("@moduleAccess.canUse('inventory')")
public class ProductController {

    private final CatalogService catalogService;

    public ProductController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = new Product(
                null, request.sku(), request.name(), request.categoryId(),
                request.unit(), request.soldByWeight(), request.satProdServ(), request.satUnit(),
                request.price(), request.cost(), true,
                request.barcodes() == null ? List.of() : request.barcodes(),
                request.attributes() == null ? Map.of() : request.attributes());
        Product saved = catalogService.createProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> byId(@PathVariable long id) {
        return catalogService.listAll().stream()
                .filter(p -> p.id() != null && p.id() == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public List<Product> search(@RequestParam("q") String query) {
        return catalogService.search(query);
    }

    @GetMapping("/barcode/{barcode}")
    public CatalogService.BarcodeLookup byBarcode(@PathVariable String barcode) {
        return catalogService.lookupByBarcode(barcode);
    }

    /** Alta de producto. Los campos del giro van en {@code attributes}. */
    public record CreateProductRequest(
            String sku,
            @NotBlank String name,
            Long categoryId,
            String unit,
            boolean soldByWeight,
            String satProdServ,
            String satUnit,
            BigDecimal price,
            BigDecimal cost,
            List<String> barcodes,
            Map<String, Object> attributes) {
    }
}
