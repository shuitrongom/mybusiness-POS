package com.mybusinesssilva.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.customers.application.CustomerService;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integración de precios especiales por cliente: el upsert crea y actualiza (sin
 * duplicar), el mapa expone producto -> precio, el delete lo quita y un precio negativo se
 * rechaza. Validan la tabla customer_price con su UNIQUE y su CHECK bajo RLS.
 */
class CustomerPriceIT extends AbstractIntegrationTest {

    @Autowired
    private LicensingService licensingService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private DataSource dataSource;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void upsertCreatesUpdatesAndDeletesSpecialPrice() {
        setup();
        long customerId = customerService.createCustomer("Cliente VIP", null, null, null, null);
        long productId = createProduct("Refresco", new BigDecimal("25.00"));

        // Alta del precio especial.
        customerService.upsertSpecialPrice(customerId, productId, new BigDecimal("18.00"));
        List<Map<String, Object>> prices = customerService.listSpecialPrices(customerId);
        assertThat(prices).hasSize(1);
        assertThat(prices.get(0)).containsEntry("productId", productId);
        assertThat((BigDecimal) prices.get(0).get("specialPrice")).isEqualByComparingTo("18.00");
        assertThat((BigDecimal) prices.get(0).get("normalPrice")).isEqualByComparingTo("25.00");

        // Segundo upsert con otro precio: ACTUALIZA, no duplica.
        customerService.upsertSpecialPrice(customerId, productId, new BigDecimal("15.00"));
        prices = customerService.listSpecialPrices(customerId);
        assertThat(prices).hasSize(1);
        assertThat((BigDecimal) prices.get(0).get("specialPrice")).isEqualByComparingTo("15.00");

        // El mapa devuelve producto -> precio.
        Map<Long, BigDecimal> map = customerService.specialPriceMap(customerId);
        assertThat(map).containsOnlyKeys(productId);
        assertThat(map.get(productId)).isEqualByComparingTo("15.00");

        // El delete lo quita.
        customerService.deleteSpecialPrice(customerId, productId);
        assertThat(customerService.listSpecialPrices(customerId)).isEmpty();
        assertThat(customerService.specialPriceMap(customerId)).isEmpty();
    }

    @Test
    void upsertRejectsNegativePrice() {
        setup();
        long customerId = customerService.createCustomer("Cliente Neg", null, null, null, null);
        long productId = createProduct("Galleta", new BigDecimal("10.00"));

        assertThatThrownBy(() ->
                customerService.upsertSpecialPrice(customerId, productId, new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upsertRejectsUnknownCustomerOrProduct() {
        setup();
        long customerId = customerService.createCustomer("Cliente Real", null, null, null, null);
        long productId = createProduct("Producto Real", new BigDecimal("5.00"));

        assertThatThrownBy(() ->
                customerService.upsertSpecialPrice(999_999L, productId, new BigDecimal("1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                customerService.upsertSpecialPrice(customerId, 999_999L, new BigDecimal("1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---

    private void setup() {
        long planId = planRepository.findByCode("ENTERPRISE").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio Precios", null, "abarrotes", planId, 1);
        TenantContext.setTenantId(business.getSchemaName());
    }

    private long createProduct(String name, BigDecimal price) {
        Product p = catalogService.createProduct(new Product(
                null, null, name, null, "pieza", false, null, null,
                price, BigDecimal.ZERO, true, List.of(), Map.of()));
        return p.id();
    }
}
