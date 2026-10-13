package com.mybusinesssilva.catalog.adapters.in.rest;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final JdbcClient jdbc;

    public ProductController(CatalogService catalogService, JdbcClient jdbc) {
        this.catalogService = catalogService;
        this.jdbc = jdbc;
    }

    /**
     * Lista todos los productos activos del negocio. Accesible para vender (cajero) o administrar.
     * Es la fuente de la cuadrícula de productos del punto de venta.
     */
    @GetMapping
    @PreAuthorize("@moduleAccess.canReadCatalog()")
    public List<Product> list() {
        return catalogService.listAll();
    }

    /** Lista las categorías del negocio (para agrupar la cuadrícula del punto de venta). */
    @GetMapping("/categories")
    @PreAuthorize("@moduleAccess.canReadCatalog()")
    public List<Map<String, Object>> categories() {
        return jdbc.sql("SELECT id, name FROM category ORDER BY name")
                .query((rs, n) -> Map.<String, Object>of(
                        "id", rs.getLong("id"),
                        "name", rs.getString("name")))
                .list();
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = toProduct(null, request);
        Product saved = catalogService.createProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(
            @PathVariable long id, @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.ok(catalogService.updateProduct(toProduct(id, request)));
    }

    /** Construye el modelo de dominio a partir del request, incluyendo los campos profesionales. */
    private Product toProduct(Long id, CreateProductRequest r) {
        boolean active = r.active() == null ? true : r.active();
        Product.ProductExtras extras = new Product.ProductExtras(
                r.description(), r.brand(), r.supplierId(),
                r.taxRate(), r.iepsRate(),
                r.price2(), r.price3(), r.price4(), r.price5(),
                r.lastCost(), r.avgCost(),
                r.minStock(), r.maxStock(), r.reorderPoint(),
                r.forSale() == null ? true : r.forSale(),
                r.trackInventory() == null ? true : r.trackInventory(),
                r.trackLots() != null && r.trackLots(),
                r.allowBelowCost() != null && r.allowBelowCost(),
                r.blocked() != null && r.blocked(),
                r.isComposite() != null && r.isComposite(),
                r.onSale() != null && r.onSale(),
                r.loyaltyPoints());
        return new Product(
                id, r.sku(), r.name(), r.categoryId(),
                r.unit(), r.soldByWeight(), r.satProdServ(), r.satUnit(),
                r.price(), r.cost(), active,
                r.barcodes() == null ? List.of() : r.barcodes(),
                r.attributes() == null ? Map.of() : r.attributes(),
                r.imageUrl(), extras);
    }

    /**
     * Elimina un producto. Si tenía ventas se desactiva (borrado lógico) para no romper el
     * histórico; si no, se borra físicamente. La respuesta indica cuál de los dos ocurrió.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable long id) {
        boolean hardDeleted = catalogService.deleteProduct(id);
        return ResponseEntity.ok(Map.of("deleted", true, "hardDeleted", hardDeleted));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduleAccess.canReadCatalog()")
    public ResponseEntity<Product> byId(@PathVariable long id) {
        return catalogService.listAll().stream()
                .filter(p -> p.id() != null && p.id() == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    @PreAuthorize("@moduleAccess.canReadCatalog()")
    public List<Product> search(@RequestParam("q") String query) {
        return catalogService.search(query);
    }

    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("@moduleAccess.canReadCatalog()")
    public CatalogService.BarcodeLookup byBarcode(@PathVariable String barcode) {
        return catalogService.lookupByBarcode(barcode);
    }

    /**
     * Alta/edición de producto. Los campos del giro van en {@code attributes}; los campos
     * profesionales (multiprecio, inventario, banderas) son opcionales y toman valores por
     * defecto sensatos si el cliente no los envía (compatibilidad con altas simples).
     */
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
            Map<String, Object> attributes,
            String imageUrl,
            // ---- Campos profesionales (todos opcionales) ----
            Boolean active,
            String description,
            String brand,
            Long supplierId,
            BigDecimal taxRate,
            BigDecimal iepsRate,
            BigDecimal price2,
            BigDecimal price3,
            BigDecimal price4,
            BigDecimal price5,
            BigDecimal lastCost,
            BigDecimal avgCost,
            BigDecimal minStock,
            BigDecimal maxStock,
            BigDecimal reorderPoint,
            Boolean forSale,
            Boolean trackInventory,
            Boolean trackLots,
            Boolean allowBelowCost,
            Boolean blocked,
            Boolean isComposite,
            Boolean onSale,
            BigDecimal loyaltyPoints) {
    }
}
