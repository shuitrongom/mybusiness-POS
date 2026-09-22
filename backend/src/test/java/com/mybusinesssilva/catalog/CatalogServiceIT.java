package com.mybusinesssilva.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.MasterProduct;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integración del catálogo y el motor multi-giro:
 * alta de producto con campos dinámicos del giro (JSONB), búsqueda por código de barras,
 * y contribución al catálogo maestro compartido (efecto de red).
 */
class CatalogServiceIT extends AbstractIntegrationTest {

    @Autowired
    private LicensingService licensingService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private PlanRepository planRepository;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void createsProductWithDynamicAttributesAndBarcode() {
        Business business = newBusiness("abarrotes");
        TenantContext.setTenantId(business.getSchemaName());

        // Se usa un código propio del negocio (no de la precarga por giro) para probar el alta
        // con atributos dinámicos sin chocar con el catálogo sembrado.
        Product product = new Product(
                null, "SKU-AGUA", "Agua Natural 1 L", null, "pieza", false,
                "50200000", "H87", new BigDecimal("15.00"), new BigDecimal("9.00"), true,
                List.of("7500000123456"),
                Map.of("expiration_date", "2027-01-01", "lot", "L123"));

        Product saved = catalogService.createProduct(product);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.attributes()).containsEntry("lot", "L123");
        assertThat(saved.barcodes()).containsExactly("7500000123456");

        // Se puede recuperar por código de barras dentro del negocio.
        CatalogService.BarcodeLookup lookup = catalogService.lookupByBarcode("7500000123456");
        assertThat(lookup.foundInBusiness()).isTrue();
        assertThat(lookup.product().name()).isEqualTo("Agua Natural 1 L");
    }

    @Test
    void masterCatalogSuggestsSeededProductToNewBusiness() {
        // Una pollería NO recibe la Coca-Cola en su precarga (esa es de abarrotes), así que el
        // catálogo maestro debe ofrecerla como sugerencia cuando se escanea su código.
        Business business = newBusiness("polleria");
        TenantContext.setTenantId(business.getSchemaName());

        CatalogService.BarcodeLookup lookup = catalogService.lookupByBarcode("7501055300201");

        assertThat(lookup.foundInBusiness()).isFalse();
        assertThat(lookup.suggestion()).isNotNull();
        assertThat(lookup.suggestion().name()).contains("Coca-Cola");
    }

    @Test
    void contributesNewBarcodeToSharedMasterCatalog() {
        Business business = newBusiness("polleria");
        TenantContext.setTenantId(business.getSchemaName());

        String newBarcode = "7500000999999";
        // Alta de un producto con un código que no existe en el maestro.
        catalogService.createProduct(new Product(
                null, null, "Producto Nuevo del Negocio", null, "pieza", false,
                null, null, new BigDecimal("10.00"), BigDecimal.ZERO, true,
                List.of(newBarcode), Map.of()));

        // Debe haberse aportado al catálogo maestro compartido (búsqueda global).
        List<MasterProduct> found = catalogService.searchMaster("Producto Nuevo del Negocio");
        assertThat(found).isNotEmpty();
        assertThat(found.get(0).barcode()).isEqualTo(newBarcode);
    }

    private Business newBusiness(String line) {
        long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
        return licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio " + line, null, line, planId, 1);
    }
}
