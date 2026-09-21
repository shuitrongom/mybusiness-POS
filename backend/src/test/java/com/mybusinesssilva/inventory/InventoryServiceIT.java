package com.mybusinesssilva.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.model.StockLevel;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pruebas de integración del inventario: entradas/salidas, kardex, alertas de stock mínimo
 * y traspasos entre sucursales, sobre un negocio aprovisionado real.
 */
class InventoryServiceIT extends AbstractIntegrationTest {

    @Autowired
    private LicensingService licensingService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private DataSource dataSource;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void purchaseAddsAndSaleSubtractsStockWithKardex() {
        setupBusiness();
        long branchId = createBranch("Matriz");
        long productId = createProduct("Refresco");

        // Entrada por compra: +100
        BigDecimal afterPurchase = inventoryService.applyMovement(
                productId, branchId, MovementType.PURCHASE, new BigDecimal("100"), "COMPRA-1", "tester");
        assertThat(afterPurchase).isEqualByComparingTo("100");

        // Salida por venta: -30
        BigDecimal afterSale = inventoryService.applyMovement(
                productId, branchId, MovementType.SALE, new BigDecimal("30"), "VENTA-1", "tester");
        assertThat(afterSale).isEqualByComparingTo("70");

        // El kardex tiene dos movimientos.
        assertThat(inventoryService.kardex(productId)).hasSize(2);

        StockLevel stock = inventoryService.stock(productId, branchId);
        assertThat(stock.quantity()).isEqualByComparingTo("70");
    }

    @Test
    void lowStockAlertTriggersAtOrBelowMinimum() {
        setupBusiness();
        long branchId = createBranch("Matriz");
        long productId = createProduct("Leche");

        inventoryService.setMinimum(productId, branchId, new BigDecimal("10"));
        inventoryService.applyMovement(
                productId, branchId, MovementType.PURCHASE, new BigDecimal("8"), "COMPRA", "tester");

        List<StockLevel> alerts = inventoryService.lowStockAlerts();
        assertThat(alerts).anyMatch(s -> s.productId() == productId && s.isBelowMinimum());
    }

    @Test
    void transferMovesStockBetweenBranches() {
        setupBusiness();
        long matriz = createBranch("Matriz");
        long sucursal = createBranch("Sucursal Norte");
        long productId = createProduct("Aceite");

        inventoryService.applyMovement(
                productId, matriz, MovementType.PURCHASE, new BigDecimal("50"), "COMPRA", "tester");

        inventoryService.transfer(productId, matriz, sucursal, new BigDecimal("20"), "tester");

        assertThat(inventoryService.stock(productId, matriz).quantity()).isEqualByComparingTo("30");
        assertThat(inventoryService.stock(productId, sucursal).quantity()).isEqualByComparingTo("20");
    }

    // --- helpers ---

    private void setupBusiness() {
        long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio Inv", null, "abarrotes", planId, 1);
        TenantContext.setTenantId(business.getSchemaName());
    }

    private long createBranch(String name) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return jdbc.queryForObject(
                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, name);
    }

    private long createProduct(String name) {
        Product p = catalogService.createProduct(new Product(
                null, null, name, null, "pieza", false, null, null,
                new BigDecimal("10"), BigDecimal.ZERO, true, List.of(), Map.of()));
        return p.id();
    }
}
