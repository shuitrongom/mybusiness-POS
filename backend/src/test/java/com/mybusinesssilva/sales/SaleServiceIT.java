package com.mybusinesssilva.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.sales.application.SaleService;
import com.mybusinesssilva.sales.application.ShiftService;
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
 * Pruebas de integración del punto de venta: la venta descuenta inventario, la idempotencia
 * evita duplicar ventas y doble descuento (offline), y el corte de caja calcula la diferencia.
 */
class SaleServiceIT extends AbstractIntegrationTest {

    @Autowired
    private LicensingService licensingService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private ShiftService shiftService;
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
    void saleDiscountsInventory() {
        long productId = setup("Refresco");
        // Stock inicial 100.
        inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                new BigDecimal("100"), "COMPRA", "tester");

        Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                List.of(new SaleLine(productId, "Refresco", new BigDecimal("5"),
                        new BigDecimal("18.00"), BigDecimal.ZERO)),
                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("100.00"))),
                null);

        SaleService.SaleResult result = saleService.registerSale(sale);

        assertThat(result.saleId()).isNotNull();
        assertThat(result.duplicated()).isFalse();
        // Inventario descontado: 100 - 5 = 95.
        assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("95");
    }

    @Test
    void idempotentSaleIsNotProcessedTwice() {
        long productId = setup("Leche");
        inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                new BigDecimal("50"), "COMPRA", "tester");

        String idem = "offline-key-001";
        Sale first = buildSale(productId, idem);
        SaleService.SaleResult r1 = saleService.registerSale(first);
        assertThat(r1.duplicated()).isFalse();

        // Se reenvía la misma venta (como al sincronizar tras estar offline).
        Sale again = buildSale(productId, idem);
        SaleService.SaleResult r2 = saleService.registerSale(again);
        assertThat(r2.duplicated()).isTrue();
        assertThat(r2.saleId()).isEqualTo(r1.saleId());

        // El inventario se descontó una sola vez: 50 - 2 = 48 (no 46).
        assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("48");
    }

    @Test
    void shiftCloseComputesCashDifference() {
        setup("X");
        long registerId = createCashRegister();
        long shiftId = shiftService.openShift(registerId, "cajero", new BigDecimal("500.00"));

        // Venta en efectivo por 200, asociada al turno.
        long productId = createProduct("Aceite");
        inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                new BigDecimal("10"), "COMPRA", "tester");
        Sale sale = Sale.complete(branchId, registerId, shiftId, "cajero", null,
                List.of(new SaleLine(productId, "Aceite", new BigDecimal("1"),
                        new BigDecimal("200.00"), BigDecimal.ZERO)),
                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("200.00"))),
                null);
        saleService.registerSale(sale);

        // Se retira 100 de efectivo.
        shiftService.recordCashMovement(shiftId, "OUT", new BigDecimal("100.00"), "Retiro", "cajero");

        // Efectivo esperado = 500 fondo + 200 ventas - 100 retiro = 600.
        // Se cuentan 590 (faltan 10).
        ShiftService.ShiftClosure closure = shiftService.closeShift(
                shiftId, "cajero", new BigDecimal("590.00"));

        assertThat(closure.expectedCash()).isEqualByComparingTo("600.00");
        assertThat(closure.difference()).isEqualByComparingTo("-10.00");
    }

    // --- helpers ---

    private long setup(String productName) {
        long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio Ventas", null, "abarrotes", planId, 1);
        TenantContext.setTenantId(business.getSchemaName());
        branchId = createBranch("Matriz");
        return createProduct(productName);
    }

    private Sale buildSale(long productId, String idem) {
        return Sale.complete(branchId, null, null, "cajero", null,
                List.of(new SaleLine(productId, "Producto", new BigDecimal("2"),
                        new BigDecimal("10.00"), BigDecimal.ZERO)),
                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("20.00"))),
                idem);
    }

    private long createBranch(String name) {
        return new JdbcTemplate(dataSource).queryForObject(
                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, name);
    }

    private long createCashRegister() {
        return new JdbcTemplate(dataSource).queryForObject(
                "INSERT INTO cash_register (branch_id, name) VALUES (?, ?) RETURNING id",
                Long.class, branchId, "Caja 1");
    }

    private long createProduct(String name) {
        Product p = catalogService.createProduct(new Product(
                null, null, name, null, "pieza", false, null, null,
                new BigDecimal("10"), BigDecimal.ZERO, true, List.of(), Map.of()));
        return p.id();
    }
}
