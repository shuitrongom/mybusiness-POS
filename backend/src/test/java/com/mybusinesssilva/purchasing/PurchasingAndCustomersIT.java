package com.mybusinesssilva.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.customers.application.CustomerService;
import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.purchasing.application.PurchasingService;
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
 * Pruebas de integración de compras/proveedores y clientes/CRM:
 * la compra aumenta inventario y genera cuenta por pagar; el cliente maneja crédito con límite
 * y programa de lealtad (acumular/canjear).
 */
class PurchasingAndCustomersIT extends AbstractIntegrationTest {

    @Autowired
    private LicensingService licensingService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private PurchasingService purchasingService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private DataSource dataSource;

    private long branchId;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void purchaseIncreasesInventoryAndCreatesPayable() {
        setup();
        long productId = createProduct("Refresco");
        long supplierId = purchasingService.createSupplier("Distribuidora X", "XAX010101AAA", null, null);

        long purchaseId = purchasingService.receivePurchase(
                supplierId, branchId, "FAC-001", true,
                List.of(new PurchasingService.PurchaseLineInput(
                        productId, new BigDecimal("100"), new BigDecimal("10.00"))),
                "comprador");

        assertThat(purchaseId).isPositive();
        // Inventario aumentó en 100.
        assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("100");

        // Se generó cuenta por pagar por 1000 (100 * 10), en estado OPEN.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        BigDecimal payableAmount = jdbc.queryForObject(
                "SELECT amount FROM account_payable WHERE purchase_id = ?", BigDecimal.class, purchaseId);
        assertThat(payableAmount).isEqualByComparingTo("1000.00");
    }

    @Test
    void customerCreditRespectsLimit() {
        setup();
        long customerId = customerService.createCustomer(
                "Cliente Credito", null, null, null, new BigDecimal("500.00"));

        // Primera cuenta por cobrar dentro del límite.
        customerService.addReceivable(customerId, 1L, new BigDecimal("300.00"), null);
        assertThat(customerService.availableCredit(customerId)).isEqualByComparingTo("200.00");

        // Segunda que excede el límite (300 + 300 > 500) debe rechazarse.
        assertThatThrownBy(() ->
                customerService.addReceivable(customerId, 2L, new BigDecimal("300.00"), null))
                .isInstanceOf(IllegalArgumentException.class);

        // Abono libera crédito.
        // (Se busca el id de la cuenta por cobrar creada.)
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long receivableId = jdbc.queryForObject(
                "SELECT id FROM account_receivable WHERE customer_id = ? ORDER BY id LIMIT 1",
                Long.class, customerId);
        customerService.payReceivable(receivableId, new BigDecimal("100.00"));
        assertThat(customerService.availableCredit(customerId)).isEqualByComparingTo("300.00");
    }

    @Test
    void loyaltyEarnAndRedeem() {
        setup();
        long customerId = customerService.createCustomer("Cliente Fiel", null, null, null, null);

        BigDecimal afterEarn = customerService.earnLoyalty(customerId, new BigDecimal("50"), "VENTA-1");
        assertThat(afterEarn).isEqualByComparingTo("50");

        BigDecimal afterRedeem = customerService.redeemLoyalty(customerId, new BigDecimal("20"), "CANJE-1");
        assertThat(afterRedeem).isEqualByComparingTo("30");

        assertThat(customerService.loyaltyBalance(customerId)).isEqualByComparingTo("30");

        // No se puede canjear más del saldo.
        assertThatThrownBy(() ->
                customerService.redeemLoyalty(customerId, new BigDecimal("1000"), "CANJE-2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---

    private void setup() {
        long planId = planRepository.findByCode("ENTERPRISE").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio Compras", null, "abarrotes", planId, 1);
        TenantContext.setTenantId(business.getSchemaName());
        branchId = new JdbcTemplate(dataSource).queryForObject(
                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, "Matriz");
    }

    private long createProduct(String name) {
        Product p = catalogService.createProduct(new Product(
                null, null, name, null, "pieza", false, null, null,
                new BigDecimal("10"), BigDecimal.ZERO, true, List.of(), Map.of()));
        return p.id();
    }
}
