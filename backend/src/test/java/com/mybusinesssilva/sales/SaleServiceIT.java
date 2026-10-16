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
 * Pruebas de integracion del punto de venta: la venta descuenta inventario, la
 * idempotencia
 * evita duplicar ventas y doble descuento (offline), y el corte de caja calcula
 * la diferencia.
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

                // Se reenvia la misma venta (como al sincronizar tras estar offline).
                Sale again = buildSale(productId, idem);
                SaleService.SaleResult r2 = saleService.registerSale(again);
                assertThat(r2.duplicated()).isTrue();
                assertThat(r2.saleId()).isEqualTo(r1.saleId());

                // El inventario se desconto una sola vez: 50 - 2 = 48 (no 46).
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

        @Test
        void classifiesCatalogSaleAsNormal() {
                long productId = setup("Galletas");
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("20"), "COMPRA", "tester");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "Galletas", new BigDecimal("2"),
                                                new BigDecimal("15.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("30.00"))),
                                null);
                SaleService.SaleResult r = saleService.registerSale(sale);

                String saleType = new JdbcTemplate(dataSource).queryForObject(
                                "SELECT sale_type FROM sale WHERE id = ?", String.class, r.saleId());
                assertThat(saleType).isEqualTo("NORMAL");
        }

        @Test
        void classifiesFreeItemSaleAsSpecialAndPersistsUnit() {
                setup("Z");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(null, "Pan por peso", new BigDecimal("1.5"),
                                                new BigDecimal("20.00"), BigDecimal.ZERO, "kg")),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("30.00"))),
                                null);
                SaleService.SaleResult r = saleService.registerSale(sale);

                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                String saleType = jdbc.queryForObject(
                                "SELECT sale_type FROM sale WHERE id = ?", String.class, r.saleId());
                String unit = jdbc.queryForObject(
                                "SELECT unit FROM sale_line WHERE sale_id = ?", String.class, r.saleId());

                assertThat(saleType).isEqualTo("SPECIAL");
                assertThat(unit).isEqualTo("kg");
        }

        @Test
        void shiftCloseSplitsNormalAndSpecialMatchingTotal() {
                setup("Split");
                long registerId = createCashRegister();
                long shiftId = shiftService.openShift(registerId, "cajero", new BigDecimal("0.00"));

                long productId = createProduct("Refresco");
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("50"), "COMPRA", "tester");

                // (a) Venta NORMAL en efectivo por 100.
                saleService.registerSale(Sale.complete(branchId, registerId, shiftId, "cajero", null,
                                List.of(new SaleLine(productId, "Refresco", new BigDecimal("5"),
                                                new BigDecimal("20.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("100.00"))),
                                null));

                // (b) Venta SPECIAL (articulo libre) con pago mixto efectivo + tarjeta = 80.
                saleService.registerSale(Sale.complete(branchId, registerId, shiftId, "cajero", null,
                                List.of(new SaleLine(null, "Pan por peso", new BigDecimal("2"),
                                                new BigDecimal("40.00"), BigDecimal.ZERO, "kg")),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("50.00")),
                                                new Payment(PaymentMethod.CARD, new BigDecimal("30.00"))),
                                null));

                // (c) Venta a credito con pago parcial: total 60, paga 20 en efectivo (NORMAL,
                // con cliente).
                long customerId = createCustomer();
                saleService.registerSale(Sale.complete(branchId, registerId, shiftId, "cajero", "cajero",
                                customerId, (short) 1, BigDecimal.ZERO, null, true,
                                List.of(new SaleLine(productId, "Refresco", new BigDecimal("3"),
                                                new BigDecimal("20.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("20.00"))),
                                null));

                ShiftService.ShiftClosure closure = shiftService.closeShift(shiftId, "cajero", null);

                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                Map<String, Object> cut = jdbc.queryForMap(
                                "SELECT total_sales, sales_count, normal_sales_count, normal_sales_total, "
                                                + "special_sales_count, special_sales_total FROM cash_cut WHERE shift_id = ? "
                                                + "ORDER BY id DESC LIMIT 1",
                                shiftId);

                BigDecimal totalSales = (BigDecimal) cut.get("total_sales");
                BigDecimal normalTotal = (BigDecimal) cut.get("normal_sales_total");
                BigDecimal specialTotal = (BigDecimal) cut.get("special_sales_total");
                int salesCount = ((Number) cut.get("sales_count")).intValue();
                int normalCount = ((Number) cut.get("normal_sales_count")).intValue();
                int specialCount = ((Number) cut.get("special_sales_count")).intValue();

                // Pagos: NORMAL 100 + credito 20 = 120 ; SPECIAL 80. total_sales = 200.
                assertThat(normalTotal.add(specialTotal)).isEqualByComparingTo(totalSales);
                assertThat(totalSales).isEqualByComparingTo("200.00");
                assertThat(normalTotal).isEqualByComparingTo("120.00");
                assertThat(specialTotal).isEqualByComparingTo("80.00");
                // 3 ventas COMPLETED en el turno: 2 NORMAL + 1 SPECIAL.
                assertThat(normalCount + specialCount).isEqualTo(salesCount);
                assertThat(normalCount).isEqualTo(2);
                assertThat(specialCount).isEqualTo(1);
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

        private long createCustomer() {
                return new JdbcTemplate(dataSource).queryForObject(
                                "INSERT INTO customer (name, credit_limit) VALUES (?, ?) RETURNING id",
                                Long.class, "Cliente credito", new BigDecimal("1000.00"));
        }

        private long createProduct(String name) {
                Product p = catalogService.createProduct(new Product(
                                null, null, name, null, "pieza", false, null, null,
                                new BigDecimal("10"), BigDecimal.ZERO, true, List.of(), Map.of()));
                return p.id();
        }
}
