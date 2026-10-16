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

        @Test
        void enforceStockSaleSubtractsWhenEnough() {
                long productId = setup("Concha");
                markEnforceStock(productId);
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("10"), "PROD:1", "panadero");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "Concha", new BigDecimal("4"),
                                                new BigDecimal("8.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("32.00"))),
                                null);
                saleService.registerSale(sale);

                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("6");
        }

        @Test
        void enforceStockSaleThatExceedsThrowsAndDoesNotPersistSale() {
                long productId = setup("Bolillo");
                markEnforceStock(productId);
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("3"), "PROD:1", "panadero");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "Bolillo", new BigDecimal("5"),
                                                new BigDecimal("4.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("20.00"))),
                                null);

                org.assertj.core.api.Assertions.assertThatThrownBy(() -> saleService.registerSale(sale))
                                .isInstanceOf(com.mybusinesssilva.inventory.domain.model.InsufficientStockException.class);

                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                // La venta NO se persistió (rollback) y el stock no cambió.
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sale", Integer.class);
                assertThat(count).isZero();
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("3");
        }

        @Test
        void enforceStockRuleDoesNotAffectUncontrolledProducts() {
                long productId = setup("Refresco");
                // enforce_stock queda en FALSE (default): se permite stock negativo como hoy.
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("2"), "COMPRA", "tester");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "Refresco", new BigDecimal("5"),
                                                new BigDecimal("10.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("50.00"))),
                                null);
                SaleService.SaleResult r = saleService.registerSale(sale);

                assertThat(r.saleId()).isNotNull();
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("-3");
        }

        @Test
        void concurrentStrictOutboundLetsOnlyOnePass() throws Exception {
                long productId = setup("Telera");
                markEnforceStock(productId);
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("5"), "PROD:1", "panadero");
                String tenant = TenantContext.getTenantId();

                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
                java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Callable<Boolean> attempt = () -> {
                        TenantContext.setTenantId(tenant);
                        try {
                                start.await();
                                inventoryService.applyOutboundStrict(productId, branchId,
                                                MovementType.SALE, new BigDecimal("4"), "SALE:x", "cajero");
                                return Boolean.TRUE;
                        } catch (com.mybusinesssilva.inventory.domain.model.InsufficientStockException e) {
                                return Boolean.FALSE;
                        } finally {
                                TenantContext.clear();
                        }
                };
                var f1 = pool.submit(attempt);
                var f2 = pool.submit(attempt);
                start.countDown();
                boolean r1 = f1.get();
                boolean r2 = f2.get();
                pool.shutdown();
                TenantContext.setTenantId(tenant);

                // Solo una de las dos salidas de 4 (sobre 5) puede pasar; la otra se rechaza.
                assertThat(r1 ^ r2).isTrue();
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("1");
        }

        @Test
        void saleWithWasteRecordsTraceabilityAndDiscountsStock() {
                long productId = setup("ConchaMerma");
                markBakery(productId);
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("10"), "PROD:1", "panadero");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "ConchaMerma", new BigDecimal("3"),
                                                new BigDecimal("3.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("9.00"))),
                                null);
                var waste = List.of(
                                new com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase.WasteLineCommand(
                                                productId, new BigDecimal("3"), new BigDecimal("5.00"),
                                                new BigDecimal("3.00")));

                SaleService.SaleResult r = saleService.registerSaleWithWaste(sale, waste);

                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                Map<String, Object> row = jdbc.queryForMap(
                                "SELECT quantity, entry_price, waste_price, difference FROM bakery_waste_sale "
                                                + "WHERE sale_id = ? AND product_id = ?",
                                r.saleId(), productId);
                assertThat((BigDecimal) row.get("quantity")).isEqualByComparingTo("3");
                assertThat((BigDecimal) row.get("entry_price")).isEqualByComparingTo("5.00");
                assertThat((BigDecimal) row.get("waste_price")).isEqualByComparingTo("3.00");
                assertThat((BigDecimal) row.get("difference")).isEqualByComparingTo("2.00");
                // Stock descontado por la venta: 10 - 3 = 7.
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("7");
        }

        @Test
        void saleWithInvalidWasteRevertsEverything() {
                long productId = setup("PanMalMerma");
                markBakery(productId);
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("10"), "PROD:1", "panadero");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "PanMalMerma", new BigDecimal("2"),
                                                new BigDecimal("9.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("18.00"))),
                                null);
                // waste_price (9) > entry_price (5): regla de negocio -> revierte toda la
                // transacción.
                var waste = List.of(
                                new com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase.WasteLineCommand(
                                                productId, new BigDecimal("2"), new BigDecimal("5.00"),
                                                new BigDecimal("9.00")));

                org.assertj.core.api.Assertions.assertThatThrownBy(
                                () -> saleService.registerSaleWithWaste(sale, waste))
                                .isInstanceOf(IllegalArgumentException.class);

                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bakery_waste_sale", Integer.class)).isZero();
                // Ni la venta ni los movimientos de salida quedaron: el stock sigue en 10.
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("10");
        }

        // --- helpers ---

        private void markEnforceStock(long productId) {
                new JdbcTemplate(dataSource).update(
                                "UPDATE product SET is_bakery = TRUE, enforce_stock = TRUE WHERE id = ?", productId);
        }

        private void markBakery(long productId) {
                new JdbcTemplate(dataSource).update(
                                "UPDATE product SET is_bakery = TRUE WHERE id = ?", productId);
        }

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
