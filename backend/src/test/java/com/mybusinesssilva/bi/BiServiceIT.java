package com.mybusinesssilva.bi;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.bi.application.AnalyticsService;
import com.mybusinesssilva.bi.application.BiService;
import com.mybusinesssilva.bi.domain.model.DashboardSummary;
import com.mybusinesssilva.bi.domain.model.ProductRanking;
import com.mybusinesssilva.bi.domain.model.PurchaseSuggestion;
import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.sales.application.SaleService;
import com.mybusinesssilva.sales.domain.model.Payment;
import com.mybusinesssilva.sales.domain.model.PaymentMethod;
import com.mybusinesssilva.sales.domain.model.Sale;
import com.mybusinesssilva.sales.domain.model.SaleLine;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pruebas de integración del BI end-to-end: se generan ventas reales y se verifican el resumen
 * del día, el ranking de productos, el análisis ABC y las sugerencias de compra.
 */
class BiServiceIT extends AbstractIntegrationTest {

    @Autowired private LicensingService licensingService;
    @Autowired private CatalogService catalogService;
    @Autowired private InventoryService inventoryService;
    @Autowired private SaleService saleService;
    @Autowired private BiService biService;
    @Autowired private AnalyticsService analyticsService;
    @Autowired private PlanRepository planRepository;
    @Autowired private DataSource dataSource;

    private long branchId;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void dashboardAndRankingReflectSales() {
        setup();
        long p1 = createProduct("Refresco", new BigDecimal("18.00"), new BigDecimal("12.00"));
        long p2 = createProduct("Pan", new BigDecimal("5.00"), new BigDecimal("3.00"));
        stock(p1, 100);
        stock(p2, 100);

        // Dos ventas: p1 vende más ingreso que p2.
        sell(p1, new BigDecimal("10"), new BigDecimal("18.00")); // 180
        sell(p2, new BigDecimal("5"), new BigDecimal("5.00"));   // 25

        DashboardSummary summary = biService.todaySummary();
        assertThat(summary.salesCount()).isEqualTo(2);
        assertThat(summary.salesTotal()).isEqualByComparingTo("205.00");
        assertThat(summary.averageTicket()).isEqualByComparingTo("102.50");

        List<ProductRanking> top = biService.topProducts(30, 10);
        assertThat(top).isNotEmpty();
        // El primero por ingreso debe ser el Refresco (180 > 25).
        assertThat(top.get(0).name()).isEqualTo("Refresco");
        assertThat(top.get(0).revenue()).isEqualByComparingTo("180.00");
    }

    @Test
    void abcClassifiesProducts() {
        setup();
        long p1 = createProduct("Estrella", new BigDecimal("100.00"), new BigDecimal("50.00"));
        long p2 = createProduct("Menor", new BigDecimal("5.00"), new BigDecimal("2.00"));
        stock(p1, 100);
        stock(p2, 100);

        sell(p1, new BigDecimal("10"), new BigDecimal("100.00")); // 1000 (dominante)
        sell(p2, new BigDecimal("1"), new BigDecimal("5.00"));    // 5

        List<ProductRanking> abc = biService.abcAnalysis(90);
        assertThat(abc).isNotEmpty();
        // El producto dominante debe clasificarse como 'A'.
        ProductRanking star = abc.stream()
                .filter(r -> r.name().equals("Estrella")).findFirst().orElseThrow();
        assertThat(star.abcClass()).isEqualTo("A");
    }

    @Test
    void purchaseSuggestionForLowStockProduct() {
        setup();
        long p = createProduct("Aceite", new BigDecimal("30.00"), new BigDecimal("20.00"));
        stock(p, 100);
        inventoryService.setMinimum(p, branchId, new BigDecimal("50"));

        // Vender para dejar el stock por debajo del mínimo: 100 - 60 = 40 (< 50).
        sell(p, new BigDecimal("60"), new BigDecimal("30.00"));

        List<PurchaseSuggestion> suggestions = analyticsService.purchaseSuggestions(15);
        assertThat(suggestions).anyMatch(s -> s.productId() == p && s.suggestedQty().signum() > 0);
    }

    // --- helpers ---

    private void setup() {
        long planId = planRepository.findByCode("ENTERPRISE").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio BI", null, "abarrotes", planId, 1);
        TenantContext.setTenantId(business.getSchemaName());
        branchId = new JdbcTemplate(dataSource).queryForObject(
                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, "Matriz");
    }

    private long createProduct(String name, BigDecimal price, BigDecimal cost) {
        Product p = catalogService.createProduct(new Product(
                null, null, name, null, "pieza", false, null, null,
                price, cost, true, List.of(), Map.of()));
        return p.id();
    }

    private void stock(long productId, int qty) {
        inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                new BigDecimal(qty), "COMPRA", "tester");
    }

    private void sell(long productId, BigDecimal qty, BigDecimal price) {
        BigDecimal total = qty.multiply(price);
        Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                List.of(new SaleLine(productId, "item", qty, price, BigDecimal.ZERO)),
                List.of(new Payment(PaymentMethod.CASH, total)), null);
        saleService.registerSale(sale);
    }
}
