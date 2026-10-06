package com.mybusinesssilva.catalog.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dispara la importación masiva del catálogo maestro desde Open Food Facts al arrancar, pero
 * <b>solo</b> cuando la propiedad {@code app.catalog.import.enabled=true} está activa. En un arranque
 * normal no hace nada, así que el llenado masivo es una acción deliberada del operador.
 *
 * <p>Uso típico (una sola vez para poblar el catálogo):
 * <pre>
 *   mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev \
 *     -Dspring-boot.run.arguments="--app.catalog.import.enabled=true"
 * </pre>
 *
 * <p>Parámetros configurables:
 * <ul>
 *   <li>{@code app.catalog.import.page-size} (default 100): productos por página de la API.</li>
 *   <li>{@code app.catalog.import.max-pages} (default 200): tope de páginas a recorrer.</li>
 * </ul>
 * Con los valores por defecto se recorren hasta 20,000 productos, suficiente para el catálogo
 * completo de México.
 */
@Component
@ConditionalOnProperty(name = "app.catalog.import.enabled", havingValue = "true")
public class CatalogImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogImportRunner.class);

    private final OpenFoodFactsImporter importer;

    public CatalogImportRunner(OpenFoodFactsImporter importer) {
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        int pageSize = intArg(args, "app.catalog.import.page-size", 100);
        int maxPages = intArg(args, "app.catalog.import.max-pages", 200);

        log.info("== Importación de catálogo maestro ACTIVADA ==");
        OpenFoodFactsImporter.ImportSummary summary = importer.importFromMexico(pageSize, maxPages);
        log.info("== Importación finalizada: {} ==", summary);
    }

    /** Lee un argumento entero de la línea de comandos, con valor por defecto si no viene o es inválido. */
    private int intArg(ApplicationArguments args, String name, int defaultValue) {
        var values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(values.get(0).trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
