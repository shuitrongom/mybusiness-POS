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

        Product product = new Product(
                null, "SKU-COCA", "Coca-Cola 600 ml", null, "pieza", false,
                "50200000", "H87", new BigDecimal("18.00"), new BigDecimal("12.00"), true,
                List.of("7501055300201"),
                Map.of("expiration_date", "2027-01-01", "lot", "L123"));

        Product saved = catalogService.createProduct(product);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.attributes()).containsEntry("lot", "L123");
        assertThat(saved.barcodes()).containsExactly("7501055300201");

        // Se puede recuperar por código de barras dentro del negocio.
        CatalogService.BarcodeLookup lookup = catalogService.lookupByBarcode("7501055300201");
        assertThat(lookup.foundInBusiness()).isTrue();
        assertThat(lookup.product().name()).isEqualTo("Coca-Cola 600 ml");
    }

    @Test
    void masterCatalogSuggestsSeededProductToNewBusiness() {
        Business business = newBusiness("abarrotes");
        TenantContext.setTenantId(business.getSchemaName());

        // El código de la Coca-Cola viene precargado en el catálogo maestro (semilla).
        // Como el negocio aún no lo tiene, debe ofrecerse como sugerencia.
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
