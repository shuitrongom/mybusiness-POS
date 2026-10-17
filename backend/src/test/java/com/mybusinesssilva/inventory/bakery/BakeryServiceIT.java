package com.mybusinesssilva.inventory.bakery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.bakery.application.BakeryService;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProductionLine;
import com.mybusinesssilva.inventory.bakery.domain.model.WasteOrigin;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pruebas de integración del subsistema de panadería: la entrada de producción
 * suma stock y crea
 * kardex, la merma registra su trazabilidad con la diferencia correcta, y las
 * consultas por día y
 * rango reportan producido/vendido/restante (incluida la sargabilidad cerca de
 * medianoche).
 */
class BakeryServiceIT extends AbstractIntegrationTest {

        @Autowired
        private LicensingService licensingService;
        @Autowired
        private CatalogService catalogService;
        @Autowired
        private InventoryService inventoryService;
        @Autowired
        private BakeryService bakeryService;
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
        void productionAddsStockAndKardex() {
                long productId = setup("Concha");

                long id = bakeryService
                                .registerProduction(production(productId, new BigDecimal("100"), new BigDecimal("5")));
                assertThat(id).isPositive();

                // Suma de existencia por el movimiento PURCHASE.
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("100");
                // El kardex tiene el movimiento de producción con referencia PROD:<id>.
                assertThat(inventoryService.kardex(productId))
                                .anyMatch(m -> m.type() == MovementType.PURCHASE
                                                && ("PROD:" + id).equals(m.reference()));

                // El folio se asignó como PROD-<id>.
                String folio = new JdbcTemplate(dataSource)
                                .queryForObject("SELECT folio FROM bakery_production WHERE id = ?", String.class, id);
                assertThat(folio).isEqualTo("PROD-" + id);
        }

        @Test
        void transferToWasteOnClosePassesNormalStockToWaste() {
                long productId = setup("Bolillo"); // product.price = 8, waste_price = 3 (ver setup)
                bakeryService.registerProduction(production(productId, new BigDecimal("50"), new BigDecimal("5")));
                // Vende 10 normales para dejar 40 de sobrante.
                inventoryService.applyOutboundStrict(productId, branchId, MovementType.SALE,
                                new BigDecimal("10"), "SALE:1", "cajero");
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("40");

                // Cierre: el sobrante (40) pasa a merma. Baja el normal a 0 y sube la merma a
                // 40.
                bakeryService.transferToWaste(productId, branchId, new BigDecimal("40"),
                                WasteOrigin.CIERRE, "cajero", null, 99L);

                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("0");
                assertThat(bakeryService.wasteStock(productId, branchId)).isEqualByComparingTo("40");

                // La trazabilidad registra el movimiento CIERRE con precios y diferencia
                // (normal 8, merma 3).
                Map<String, Object> row = new JdbcTemplate(dataSource).queryForMap(
                                "SELECT origin, quantity, normal_price, waste_price, difference "
                                                + "FROM bakery_waste_movement WHERE product_id = ? AND origin = 'CIERRE'",
                                productId);
                assertThat(row.get("origin")).isEqualTo("CIERRE");
                assertThat((BigDecimal) row.get("quantity")).isEqualByComparingTo("40");
                assertThat((BigDecimal) row.get("normal_price")).isEqualByComparingTo("8");
                assertThat((BigDecimal) row.get("waste_price")).isEqualByComparingTo("3");
                assertThat((BigDecimal) row.get("difference")).isEqualByComparingTo("200"); // (8-3)*40
        }

        @Test
        void sellWasteDeductsFromWasteStockAndRejectsOversell() {
                long productId = setup("Concha");
                bakeryService.registerProduction(production(productId, new BigDecimal("20"), new BigDecimal("5")));
                // Pasa 20 a merma (manual).
                bakeryService.transferToWaste(productId, branchId, new BigDecimal("20"),
                                WasteOrigin.MANUAL, "cajero", null, null);
                assertThat(bakeryService.wasteStock(productId, branchId)).isEqualByComparingTo("20");

                // Crea una venta mínima a la que ligar la merma.
                long saleId = new JdbcTemplate(dataSource).queryForObject(
                                "INSERT INTO sale (branch_id, cashier, status) VALUES (?, ?, 'COMPLETED') RETURNING id",
                                Long.class, branchId, "cajero");

                // Vende 5 como merma: descuenta de merma (20 -> 15) y registra movimiento
                // VENTA.
                bakeryService.sellWaste(saleId, branchId, "cajero",
                                List.of(new com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase.WasteLineCommand(
                                                productId, new BigDecimal("5"))));
                assertThat(bakeryService.wasteStock(productId, branchId)).isEqualByComparingTo("15");

                // No-sobreventa de merma: pedir 100 de merma (solo hay 15) revienta y no
                // descuenta.
                assertThatThrownBy(() -> bakeryService.sellWaste(saleId, branchId, "cajero",
                                List.of(new com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase.WasteLineCommand(
                                                productId, new BigDecimal("100")))))
                                .isInstanceOf(com.mybusinesssilva.inventory.domain.model.InsufficientStockException.class);
                assertThat(bakeryService.wasteStock(productId, branchId)).isEqualByComparingTo("15");
        }

        @Test
        void dailyStockReportsProducedSoldAndRemaining() {
                long productId = setup("Telera");
                // Producido 100 hoy.
                bakeryService.registerProduction(production(productId, new BigDecimal("100"), new BigDecimal("4")));
                // Vendido 30 hoy (movimiento SALE con referencia SALE:*).
                inventoryService.applyMovement(productId, branchId, MovementType.SALE,
                                new BigDecimal("30"), "SALE:1", "cajero");

                Map<String, Object> daily = bakeryService.dailyStock(productId, branchId, LocalDate.now());
                assertThat((BigDecimal) daily.get("producido")).isEqualByComparingTo("100");
                assertThat((BigDecimal) daily.get("vendido")).isEqualByComparingTo("30");
                assertThat((BigDecimal) daily.get("restanteDelDia")).isEqualByComparingTo("70");
                // existenciaActual presente porque la consulta es de hoy.
                assertThat(daily).containsKey("existenciaActual");
        }

        @Test
        void soldQueryIsSargableAcrossMidnight() {
                long productId = setup("Birote");
                bakeryService.registerProduction(production(productId, new BigDecimal("10"), new BigDecimal("4")));
                inventoryService.applyMovement(productId, branchId, MovementType.SALE,
                                new BigDecimal("3"), "SALE:1", "cajero");

                // Un movimiento con created_at cercano a medianoche en la zona del negocio debe
                // caer en el
                // día natural correcto. Forzamos un created_at a las 23:59 hora de
                // America/Mexico_City de hoy.
                new JdbcTemplate(dataSource).update(
                                "UPDATE inventory_movement SET created_at = "
                                                + "((now() AT TIME ZONE 'America/Mexico_City')::date + time '23:59') "
                                                + "AT TIME ZONE 'America/Mexico_City' "
                                                + "WHERE product_id = ? AND movement_type = 'SALE'",
                                productId);

                Map<String, Object> daily = bakeryService.dailyStock(productId, branchId, LocalDate.now());
                // El rango sobre created_at crudo captura el movimiento del día natural de hoy.
                assertThat((BigDecimal) daily.get("vendido")).isEqualByComparingTo("3");
        }

        // --- helpers ---

        private BakeryProduction production(long productId, BigDecimal qty, BigDecimal entryPrice) {
                BakeryProductionLine line = new BakeryProductionLine(
                                null, productId, "pan", qty, "pieza", entryPrice, BigDecimal.ZERO,
                                entryPrice.multiply(qty));
                return new BakeryProduction(null, null, branchId, "PRODUCED", null,
                                qty, entryPrice.multiply(qty), null, "panadero", "CASHIER", null, List.of(line));
        }

        private long setup(String productName) {
                long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
                Business business = licensingService.createBusiness(
                                "admin@mybusinesssilva.com", "Panaderia IT", null, "panaderia", planId, 1);
                TenantContext.setTenantId(business.getSchemaName());
                branchId = new JdbcTemplate(dataSource).queryForObject(
                                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, "Matriz");
                Product p = catalogService.createProduct(new Product(
                                null, null, productName, null, "pieza", false, null, null,
                                new BigDecimal("8"), BigDecimal.ZERO, true, List.of(), Map.of()));
                // Precio de merma fijo = 3 (el POS lo aplica al vender merma; el cajero no lo
                // teclea).
                new JdbcTemplate(dataSource).update(
                                "UPDATE product SET waste_price = 3 WHERE id = ?", p.id());
                return p.id();
        }
}
