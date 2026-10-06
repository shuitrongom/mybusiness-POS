package com.mybusinesssilva.catalog.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Importador masivo del catálogo maestro a partir de <b>Open Food Facts México</b>, base de datos
 * abierta (dominio público) de productos reales con su código de barras EAN-13 verificado.
 *
 * <p>Recorre la API de búsqueda de OFF filtrando por país México, página por página, y por cada
 * producto con código de barras y nombre válidos lo clasifica ({@link CategoryClassifier}) en una
 * categoría de negocio con su clave SAT, e inserta en {@code admin.master_product} de forma
 * idempotente (no duplica por código de barras).
 *
 * <p>No corre en cada arranque: lo dispara {@link CatalogImportRunner} solo cuando se activa la
 * propiedad {@code app.catalog.import.enabled=true}. Así el llenado masivo es una operación
 * deliberada, no un efecto colateral del inicio.
 */
@Service
public class OpenFoodFactsImporter {

    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsImporter.class);

    /** Endpoint de búsqueda de OFF México. Devuelve JSON paginado. */
    private static final String BASE_URL = "https://mx.openfoodfacts.org";
    private static final String SEARCH_PATH =
            "/cgi/search.pl?action=process&tagtype_0=countries&tag_contains_0=contains"
                    + "&tag_0=mexico&json=1&page_size={pageSize}&page={page}"
                    + "&fields=code,product_name,product_name_es,brands,categories,quantity";

    /** Longitud típica de un EAN/UPC. Filtra basura (códigos muy cortos o no numéricos). */
    private static final int MIN_BARCODE_LEN = 8;
    private static final int MAX_BARCODE_LEN = 14;

    private final JdbcClient jdbc;
    private final RestClient restClient;

    public OpenFoodFactsImporter(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "MyBusinessSilvaPOS/1.0 (catalog seeding; contacto@mybusinesssilva.com)")
                .build();
    }

    /**
     * Ejecuta la importación recorriendo hasta {@code maxPages} páginas de {@code pageSize}
     * productos. Devuelve un resumen con el conteo de insertados, omitidos y errores.
     *
     * @param pageSize productos por página (OFF admite hasta ~100)
     * @param maxPages número máximo de páginas a recorrer (límite de seguridad)
     * @return resumen de la corrida
     */
    public ImportSummary importFromMexico(int pageSize, int maxPages) {
        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger fetched = new AtomicInteger();
        int emptyPages = 0;

        log.info("Iniciando importación Open Food Facts México (pageSize={}, maxPages={})", pageSize, maxPages);

        for (int page = 1; page <= maxPages; page++) {
            OffResponse response = fetchPage(pageSize, page);
            if (response == null || response.products() == null || response.products().isEmpty()) {
                emptyPages++;
                if (emptyPages >= 2) {
                    log.info("Sin más productos tras la página {}. Terminando.", page);
                    break;
                }
                continue;
            }
            emptyPages = 0;

            for (OffProduct product : response.products()) {
                fetched.incrementAndGet();
                if (persist(product)) {
                    inserted.incrementAndGet();
                } else {
                    skipped.incrementAndGet();
                }
            }

            if (page % 10 == 0) {
                log.info("Progreso: página {} — insertados {}, omitidos {}",
                        page, inserted.get(), skipped.get());
            }
        }

        ImportSummary summary = new ImportSummary(fetched.get(), inserted.get(), skipped.get());
        log.info("Importación terminada: {}", summary);
        return summary;
    }

    /** Obtiene una página de la API de OFF; devuelve null ante error de red (para no abortar todo). */
    private OffResponse fetchPage(int pageSize, int page) {
        try {
            return restClient.get()
                    .uri(SEARCH_PATH, Map.of("pageSize", pageSize, "page", page))
                    .retrieve()
                    .body(OffResponse.class);
        } catch (RuntimeException ex) {
            log.warn("Error al obtener la página {} de OFF: {}", page, ex.getMessage());
            return null;
        }
    }

    /**
     * Inserta el producto en el catálogo maestro si tiene código de barras y nombre válidos.
     *
     * @return true si se insertó una fila nueva; false si se omitió o ya existía
     */
    private boolean persist(OffProduct product) {
        String barcode = clean(product.code());
        if (!isValidBarcode(barcode)) {
            return false;
        }
        String name = resolveName(product);
        if (name == null || name.isBlank()) {
            return false;
        }
        // El nombre y la marca respetan los límites de columna del esquema.
        name = truncate(name, 300);
        String brand = truncate(firstOf(clean(product.brands())), 160);

        CategoryClassifier.Classification c =
                CategoryClassifier.classify(product.categories(), name);

        try {
            int rows = jdbc.sql("""
                    INSERT INTO admin.master_product
                        (barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
                    VALUES (:barcode, :name, :brand, :category, 'pieza', :prodServ, :unit, 'USER')
                    ON CONFLICT (barcode) DO NOTHING
                    """)
                    .param("barcode", barcode)
                    .param("name", name)
                    .param("brand", brand)
                    .param("category", c.category())
                    .param("prodServ", c.satProdServ())
                    .param("unit", c.satUnit())
                    .update();
            return rows > 0;
        } catch (RuntimeException ex) {
            // Un producto malformado no debe tumbar la importación completa.
            log.debug("Producto omitido (barcode {}): {}", barcode, ex.getMessage());
            return false;
        }
    }

    /** Prefiere el nombre en español de OFF; si no existe, usa el nombre genérico. */
    private String resolveName(OffProduct product) {
        String es = clean(product.productNameEs());
        if (es != null && !es.isBlank()) {
            return es;
        }
        return clean(product.productName());
    }

    private static boolean isValidBarcode(String barcode) {
        if (barcode == null || barcode.length() < MIN_BARCODE_LEN || barcode.length() > MAX_BARCODE_LEN) {
            return false;
        }
        for (int i = 0; i < barcode.length(); i++) {
            if (!Character.isDigit(barcode.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Toma solo la primera marca cuando OFF trae varias separadas por coma. */
    private static String firstOf(String value) {
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        return comma > 0 ? value.substring(0, comma).trim() : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Resumen de una corrida de importación. */
    public record ImportSummary(int fetched, int inserted, int skipped) {
        @Override
        public String toString() {
            return "leídos=" + fetched + ", insertados=" + inserted + ", omitidos=" + skipped;
        }
    }

    // ---- DTOs de la respuesta JSON de OFF (solo los campos que usamos) ----

    /** Respuesta paginada de la API de búsqueda de OFF. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OffResponse(int count, int page, List<OffProduct> products) {}

    /** Producto de OFF. Se mapean explícitamente los nombres snake_case del JSON. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OffProduct(
            @JsonProperty("code") String code,
            @JsonProperty("product_name") String productName,
            @JsonProperty("product_name_es") String productNameEs,
            @JsonProperty("brands") String brands,
            @JsonProperty("categories") String categories,
            @JsonProperty("quantity") String quantity) {}
}
