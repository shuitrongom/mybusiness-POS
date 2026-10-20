package com.mybusinesssilva.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.bakery.application.BakeryService;
import com.mybusinesssilva.inventory.bakery.domain.model.WasteOrigin;
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
import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.platform.security.Roles;
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
        private BakeryService bakeryService;
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
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        @Test
        void saleDiscountsInventory() {
                long productId = setup("Refresco");
                setPrice(productId, new BigDecimal("18.00"));
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("100"), "COMPRA", "tester");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "Refresco", new BigDecimal("5"),
                                                new BigDecimal("18.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("100.00"))),
                                null);

                SaleService.SaleResult result = saleService.registerSale(toCommand(sale));

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
                SaleService.SaleResult r1 = saleService.registerSale(toCommand(first));
                assertThat(r1.duplicated()).isFalse();

                // Se reenvia la misma venta (como al sincronizar tras estar offline).
                Sale again = buildSale(productId, idem);
                SaleService.SaleResult r2 = saleService.registerSale(toCommand(again));
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
                setPrice(productId, new BigDecimal("200.00"));
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("10"), "COMPRA", "tester");
                Sale sale = Sale.complete(branchId, registerId, shiftId, "cajero", null,
                                List.of(new SaleLine(productId, "Aceite", new BigDecimal("1"),
                                                new BigDecimal("200.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("200.00"))),
                                null);
                saleService.registerSale(toCommand(sale));

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
                setPrice(productId, new BigDecimal("15.00"));
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("20"), "COMPRA", "tester");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "Galletas", new BigDecimal("2"),
                                                new BigDecimal("15.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("30.00"))),
                                null);
                SaleService.SaleResult r = saleService.registerSale(toCommand(sale));

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
                SaleService.SaleResult r = saleService.registerSale(toCommand(sale));

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
                setPrice(productId, new BigDecimal("20.00"));
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("50"), "COMPRA", "tester");

                // (a) Venta NORMAL en efectivo por 100.
                saleService.registerSale(toCommand(Sale.complete(branchId, registerId, shiftId, "cajero", null,
                                List.of(new SaleLine(productId, "Refresco", new BigDecimal("5"),
                                                new BigDecimal("20.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("100.00"))),
                                null)));

                // (b) Venta SPECIAL (articulo libre) con pago mixto efectivo + tarjeta = 80.
                saleService.registerSale(toCommand(Sale.complete(branchId, registerId, shiftId, "cajero", null,
                                List.of(new SaleLine(null, "Pan por peso", new BigDecimal("2"),
                                                new BigDecimal("40.00"), BigDecimal.ZERO, "kg")),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("50.00")),
                                                new Payment(PaymentMethod.CARD, new BigDecimal("30.00"))),
                                null)));

                // (c) Venta a credito con pago parcial: total 60, paga 20 en efectivo (NORMAL,
                // con cliente).
                long customerId = createCustomer();
                saleService.registerSale(toCommand(Sale.complete(branchId, registerId, shiftId, "cajero", "cajero",
                                customerId, (short) 1, BigDecimal.ZERO, null, true,
                                List.of(new SaleLine(productId, "Refresco", new BigDecimal("3"),
                                                new BigDecimal("20.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("20.00"))),
                                null)));

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
                setPrice(productId, new BigDecimal("8.00"));
                markEnforceStock(productId);
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("10"), "PROD:1", "panadero");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "Concha", new BigDecimal("4"),
                                                new BigDecimal("8.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("32.00"))),
                                null);
                saleService.registerSale(toCommand(sale));

                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("6");
        }

        @Test
        void enforceStockSaleThatExceedsThrowsAndDoesNotPersistSale() {
                long productId = setup("Bolillo");
                setPrice(productId, new BigDecimal("4.00"));
                markEnforceStock(productId);
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("3"), "PROD:1", "panadero");

                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "Bolillo", new BigDecimal("5"),
                                                new BigDecimal("4.00"), BigDecimal.ZERO)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("20.00"))),
                                null);

                org.assertj.core.api.Assertions.assertThatThrownBy(
                                () -> saleService.registerSale(toCommand(sale)))
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
                SaleService.SaleResult r = saleService.registerSale(toCommand(sale));

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
        void saleWithWasteDiscountsWasteStockNotNormalStock() {
                long productId = setup("ConchaMerma");
                setWastePrice(productId, new BigDecimal("3.00")); // precio de merma fijo
                // Produce 10 y pasa 10 a merma: stock normal 0, merma 10.
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("10"), "PROD:1", "panadero");
                bakeryService.transferToWaste(productId, branchId, new BigDecimal("10"),
                                WasteOrigin.MANUAL, "cajero", null, null);
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("0");
                assertThat(bakeryService.wasteStock(productId, branchId)).isEqualByComparingTo("10");

                // Venta de merma de 3 piezas: el renglón va marcado waste=true y al precio de
                // merma.
                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "ConchaMerma", new BigDecimal("3"),
                                                new BigDecimal("3.00"), BigDecimal.ZERO, "pieza", true)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("9.00"))),
                                null);

                SaleService.SaleResult r = saleService.registerSaleWithWaste(toCommand(sale));

                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                Map<String, Object> row = jdbc.queryForMap(
                                "SELECT origin, quantity, normal_price, waste_price, difference "
                                                + "FROM bakery_waste_movement WHERE sale_id = ? AND product_id = ?",
                                r.saleId(), productId);
                assertThat(row.get("origin")).isEqualTo("VENTA");
                assertThat((BigDecimal) row.get("quantity")).isEqualByComparingTo("3");
                assertThat((BigDecimal) row.get("waste_price")).isEqualByComparingTo("3.00");
                // La merma baja de 10 a 7; el stock normal NO se tocó (sigue en 0).
                assertThat(bakeryService.wasteStock(productId, branchId)).isEqualByComparingTo("7");
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("0");
        }

        @Test
        void saleWithWasteOversellRevertsEverything() {
                long productId = setup("PanMalMerma");
                setWastePrice(productId, new BigDecimal("3.00"));
                // Produce 10 y pasa solo 2 a merma: hay 2 en merma, 8 en normal.
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("10"), "PROD:1", "panadero");
                bakeryService.transferToWaste(productId, branchId, new BigDecimal("2"),
                                WasteOrigin.MANUAL, "cajero", null, null);

                // Intenta vender 5 como merma habiendo solo 2: no-sobreventa de merma ->
                // revierte todo.
                Sale sale = Sale.complete(branchId, null, null, "cajero", null,
                                List.of(new SaleLine(productId, "PanMalMerma", new BigDecimal("5"),
                                                new BigDecimal("3.00"), BigDecimal.ZERO, "pieza", true)),
                                List.of(new Payment(PaymentMethod.CASH, new BigDecimal("15.00"))),
                                null);

                org.assertj.core.api.Assertions.assertThatThrownBy(
                                () -> saleService.registerSaleWithWaste(toCommand(sale)))
                                .isInstanceOf(com.mybusinesssilva.inventory.domain.model.InsufficientStockException.class);

                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                // Ni la venta ni el movimiento de merma quedaron (transacción revertida).
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale", Integer.class)).isZero();
                assertThat(jdbc.queryForObject(
                                "SELECT COUNT(*) FROM bakery_waste_movement WHERE origin = 'VENTA'", Integer.class))
                                .isZero();
                // La merma sigue en 2 y el stock normal en 8.
                assertThat(bakeryService.wasteStock(productId, branchId)).isEqualByComparingTo("2");
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("8");
        }

        // --- helpers ---

        private void markEnforceStock(long productId) {
                new JdbcTemplate(dataSource).update(
                                "UPDATE product SET enforce_stock = TRUE WHERE id = ?", productId);
        }

        private void setWastePrice(long productId, BigDecimal wastePrice) {
                new JdbcTemplate(dataSource).update(
                                "UPDATE product SET waste_price = ? WHERE id = ?", wastePrice, productId);
        }

        @Test
        void closeShiftInBakeryMovesLeftoverToWaste() {
                // Negocio panadería + cajero autenticado con el módulo bakery (gatea el
                // candado).
                long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
                Business business = licensingService.createBusiness(
                                "admin@mybusinesssilva.com", "Panaderia Cierre", null, "panaderia", planId, 1);
                TenantContext.setTenantId(business.getSchemaName());
                branchId = createBranch("Matriz");
                long productId = createProduct("Concha");
                authenticateBakery(business.getSchemaName());

                // Produce 50 y vende 10 normales: quedan 40 de sobrante en stock normal.
                inventoryService.applyMovement(productId, branchId, MovementType.PURCHASE,
                                new BigDecimal("50"), "PROD:1", "panadero");
                inventoryService.applyOutboundStrict(productId, branchId, MovementType.SALE,
                                new BigDecimal("10"), "SALE:1", "cajero");

                long registerId = createCashRegister();
                long shiftId = shiftService.openShift(registerId, "cajero", new BigDecimal("0.00"),
                                branchId, null, null, false);

                shiftService.closeShift(shiftId, "cajero", null);

                // Candado: el sobrante (40) pasó a merma; stock normal en 0, merma en 40.
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("0");
                assertThat(bakeryService.wasteStock(productId, branchId)).isEqualByComparingTo("40");
                Integer cierreMovs = new JdbcTemplate(dataSource).queryForObject(
                                "SELECT COUNT(*) FROM bakery_waste_movement WHERE origin = 'CIERRE' AND shift_id = ?",
                                Integer.class, shiftId);
                assertThat(cierreMovs).isEqualTo(1);
        }

        private void authenticateBakery(String tenant) {
                AuthenticatedUser user = new AuthenticatedUser(
                                "cajero@panaderia.mx", tenant,
                                List.of(Roles.CASHIER), List.of("sales", "cash", "bakery"));
                var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                user, null, org.springframework.security.core.authority.AuthorityUtils.NO_AUTHORITIES);
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
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

        /**
         * Convierte un agregado {@link Sale} (como lo construían estas pruebas) en el
         * {@link SaleService.SaleCommand} crudo que ahora recibe el servicio, para que
         * el servicio recompute el precio autoritativo (ALTA-1). El precio del renglón
         * viaja como {@code unitPriceFront} crudo.
         */
        private SaleService.SaleCommand toCommand(Sale sale) {
                return new SaleService.SaleCommand(
                                sale.getBranchId(), sale.getCashRegisterId(), sale.getShiftId(),
                                sale.getCashier(), sale.getSalesperson(), sale.getCustomerId(),
                                sale.getPriceListId(), sale.getGlobalDiscount(), sale.getNote(),
                                sale.isOnCredit(),
                                sale.getLines().stream().map(l -> new SaleService.RawLine(
                                                l.productId(), l.description(), l.quantity(), l.unitPrice(),
                                                l.discount(), l.unit(), l.waste())).toList(),
                                sale.getPayments(), sale.getIdempotencyKey());
        }

        private void setPrice(long productId, BigDecimal price) {
                new JdbcTemplate(dataSource).update(
                                "UPDATE product SET price = ? WHERE id = ?", price, productId);
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
