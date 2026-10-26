package com.mybusinesssilva.sales.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.catalog.application.CatalogService;
import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.bakery.application.BakeryService;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProductionLine;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.sales.application.ShiftService.CloseMode;
import com.mybusinesssilva.sales.application.ShiftService.CloseOptions;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pruebas de integración del cierre inteligente (Opción B flexible, FEAT-001):
 * detección del primer turno del día (orden MX), señal {@link CloseOptions},
 * persistencia del {@code close_mode}, bloqueo diario que distingue TURNO de
 * FIN_DIA, reset del modo al reabrir, y el candado de merma solo en FIN_DIA.
 *
 * <p>
 * El shell de agentes está roto; esta suite la EJECUTA el dueño. Se sigue el
 * patrón de {@code BakeryServiceIT} (negocio + tenant + sucursal sembrados).
 */
class ShiftCloseOptionsIT extends AbstractIntegrationTest {

        @Autowired
        private ShiftService shiftService;
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
        private long registerId;

        @AfterEach
        void clear() {
                TenantContext.clear();
        }

        // --- closeOptionsFor ---

        @Test
        void singleShiftIsFirstOfDaySuggestsTurnoAndStrongConfirm() {
                tenant();
                long s = openShift("cajera", registerId, branchId, 0);

                CloseOptions opts = shiftService.closeOptionsFor(s).orElseThrow();

                assertThat(opts.shiftId()).isEqualTo(s);
                assertThat(opts.isFirstOfDay()).isTrue();
                assertThat(opts.onlyShiftSoFar()).isTrue();
                assertThat(opts.totalShiftsToday()).isEqualTo(1);
                assertThat(opts.suggestedMode()).isEqualTo("TURNO");
                assertThat(opts.allowFinDia()).isTrue();
                assertThat(opts.requireStrongConfirm()).isTrue();
                assertThat(opts.otherOpenInBranch()).isFalse();
        }

        @Test
        void secondShiftOfDaySuggestsFinDiaWithoutStrongConfirm() {
                tenant();
                long first = openShift("cajera", registerId, branchId, -120);
                shiftService.closeShift(first, "cajera", BigDecimal.ZERO, CloseMode.TURNO);
                long second = openShift("relevo", registerId, branchId, -5);

                CloseOptions opts = shiftService.closeOptionsFor(second).orElseThrow();

                assertThat(opts.isFirstOfDay()).isFalse();
                assertThat(opts.onlyShiftSoFar()).isFalse();
                assertThat(opts.totalShiftsToday()).isEqualTo(2);
                assertThat(opts.suggestedMode()).isEqualTo("FIN_DIA");
                assertThat(opts.requireStrongConfirm()).isFalse();
        }

        @Test
        void thirdShiftOfDayCountsAllAndNotFirst() {
                tenant();
                long a = openShift("c1", registerId, branchId, -180);
                shiftService.closeShift(a, "c1", BigDecimal.ZERO, CloseMode.TURNO);
                long b = openShift("c2", registerId, branchId, -120);
                shiftService.closeShift(b, "c2", BigDecimal.ZERO, CloseMode.TURNO);
                long c = openShift("c3", registerId, branchId, -5);

                CloseOptions opts = shiftService.closeOptionsFor(c).orElseThrow();

                assertThat(opts.isFirstOfDay()).isFalse();
                assertThat(opts.totalShiftsToday()).isEqualTo(3);
                assertThat(opts.suggestedMode()).isEqualTo("FIN_DIA");
        }

        @Test
        void firstShiftStillOpenIsDetectedAsFirst() {
                tenant();
                long first = openShift("cajera", registerId, branchId, -60);
                // El primero sigue OPEN: la detección cuenta cerrados Y abiertos.
                CloseOptions opts = shiftService.closeOptionsFor(first).orElseThrow();

                assertThat(opts.isFirstOfDay()).isTrue();
                assertThat(opts.suggestedMode()).isEqualTo("TURNO");
        }

        @Test
        void branchNullShortCircuitsToConservativeTurno() {
                tenant();
                // Dos turnos legados SIN sucursal el mismo día MX: aunque existan varios NULL,
                // el cortocircuito del paso 0 debe dar TURNO + confirmación fuerte (hallazgo 1).
                openShift("legacy1", registerId, null, -120);
                long target = openShift("legacy2", register2(), null, -5);

                CloseOptions opts = shiftService.closeOptionsFor(target).orElseThrow();

                assertThat(opts.isFirstOfDay()).isTrue();
                assertThat(opts.onlyShiftSoFar()).isTrue();
                assertThat(opts.totalShiftsToday()).isEqualTo(1);
                assertThat(opts.suggestedMode()).isEqualTo("TURNO");
                assertThat(opts.requireStrongConfirm()).isTrue();
                assertThat(opts.otherOpenInBranch()).isFalse();
        }

        @Test
        void missingShiftReturnsEmpty() {
                tenant();
                Optional<CloseOptions> opts = shiftService.closeOptionsFor(9_999_999L);
                assertThat(opts).isEmpty();
        }

        @Test
        void twoOpenRegistersInBranchFlagOtherOpen() {
                tenant();
                long r2 = register2();
                long target = openShift("cajera", registerId, branchId, -60);
                openShift("cajera2", r2, branchId, -30); // otra caja OPEN en la MISMA sucursal

                CloseOptions opts = shiftService.closeOptionsFor(target).orElseThrow();

                assertThat(opts.otherOpenInBranch()).isTrue();
        }

        @Test
        void orderIsPhysicalByOpenedAt() {
                tenant();
                // Sembrar el "primero" con opened_at anterior aunque se cree después.
                long later = openShift("cajera", registerId, branchId, -5);
                long earlier = openShift("relevo", register2(), branchId, -300);

                // El de opened_at más antiguo es el primero del día (seq=1).
                assertThat(shiftService.closeOptionsFor(earlier).orElseThrow().isFirstOfDay()).isTrue();
                assertThat(shiftService.closeOptionsFor(later).orElseThrow().isFirstOfDay()).isFalse();
        }

        // --- bloqueo diario: solo FIN_DIA bloquea ---

        @Test
        void turnoCloseDoesNotBlockRegister() {
                tenant();
                long s = openShift("cajera", registerId, branchId, -60);
                shiftService.closeShift(s, "cajera", BigDecimal.ZERO, CloseMode.TURNO);

                assertThat(shiftService.hasClosedTodayOnRegister("cajera", registerId)).isFalse();
                assertThat(shiftService.cashierClosedToday("cajera")).isFalse();
        }

        @Test
        void finDiaCloseBlocksRegister() {
                tenant();
                long s = openShift("cajera", registerId, branchId, -60);
                shiftService.closeShift(s, "cajera", BigDecimal.ZERO, CloseMode.FIN_DIA);

                assertThat(shiftService.hasClosedTodayOnRegister("cajera", registerId)).isTrue();
                assertThat(shiftService.cashierClosedToday("cajera")).isTrue();
        }

        @Test
        void legacyNullCloseModeCountsAsBlocked() {
                tenant();
                long s = openShift("cajera", registerId, branchId, -60);
                shiftService.closeShift(s, "cajera", BigDecimal.ZERO, CloseMode.TURNO);
                // Simula dato legado: cerrado antes de V46 (close_mode NULL).
                jdbc().update("UPDATE shift SET close_mode = NULL WHERE id = ?", s);

                assertThat(shiftService.hasClosedTodayOnRegister("cajera", registerId)).isTrue();
                assertThat(shiftService.cashierClosedToday("cajera")).isTrue();
        }

        // --- persistencia y reset de close_mode ---

        @Test
        void closeShiftPersistsCloseMode() {
                tenant();
                long turno = openShift("c1", registerId, branchId, -60);
                shiftService.closeShift(turno, "c1", BigDecimal.ZERO, CloseMode.TURNO);
                assertThat(closeModeOf(turno)).isEqualTo("TURNO");

                long finDia = openShift("c2", register2(), branchId, -60);
                shiftService.closeShift(finDia, "c2", BigDecimal.ZERO, CloseMode.FIN_DIA);
                assertThat(closeModeOf(finDia)).isEqualTo("FIN_DIA");
        }

        @Test
        void reopenShiftResetsCloseModeToNull() {
                tenant();
                long s = openShift("cajera", registerId, branchId, 0);
                shiftService.closeShift(s, "cajera", BigDecimal.ZERO, CloseMode.FIN_DIA);
                assertThat(closeModeOf(s)).isEqualTo("FIN_DIA");

                shiftService.reopenShift(s, "owner", "corregir corte", "JUnit");
                assertThat(closeModeOf(s)).isNull();
                // Tras reabrir deja de contar como día cerrado.
                assertThat(shiftService.hasClosedTodayOnRegister("cajera", registerId)).isFalse();
        }

        // --- candado de merma solo en FIN_DIA ---

        @Test
        void mermaLockFiresOnlyOnFinDia() {
                tenant();
                long productId = setupBakeryProduct("Bolillo");
                // Produce 50 (suma stock normal) y deja 40 de sobrante.
                bakeryService.registerProduction(production(productId, new BigDecimal("50"), new BigDecimal("5")));
                inventoryService.applyOutboundStrict(productId, branchId, MovementType.SALE,
                                new BigDecimal("10"), "SALE:1", "cajera");
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("40");

                // Cierre TURNO: NO manda a merma; el sobrante sigue vendible.
                long turno = openShift("cajera", registerId, branchId, 0);
                shiftService.closeShift(turno, "cajera", BigDecimal.ZERO, CloseMode.TURNO);
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("40");
                assertThat(bakeryService.wasteStock(productId, branchId)).isEqualByComparingTo("0");

                // Cierre FIN_DIA: SÍ manda el sobrante a merma.
                long finDia = openShift("cajera", registerId, branchId, 0);
                shiftService.closeShift(finDia, "cajera", BigDecimal.ZERO, CloseMode.FIN_DIA);
                assertThat(inventoryService.stock(productId, branchId).quantity()).isEqualByComparingTo("0");
                assertThat(bakeryService.wasteStock(productId, branchId)).isEqualByComparingTo("40");
        }

        // --- helpers ---

        private JdbcTemplate jdbc() {
                return new JdbcTemplate(dataSource);
        }

        private String closeModeOf(long shiftId) {
                return jdbc().queryForObject("SELECT close_mode FROM shift WHERE id = ?", String.class, shiftId);
        }

        /** Crea negocio panadería + tenant + sucursal + una caja. */
        private void tenant() {
                long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
                Business business = licensingService.createBusiness(
                                "admin@mybusinesssilva.com", "Panaderia Turnos", null, "panaderia", planId, 1);
                TenantContext.setTenantId(business.getSchemaName());
                branchId = jdbc().queryForObject(
                                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, "Matriz");
                registerId = jdbc().queryForObject(
                                "INSERT INTO cash_register (name, branch_id) VALUES (?, ?) RETURNING id",
                                Long.class, "Caja 1", branchId);
        }

        private long register2() {
                return jdbc().queryForObject(
                                "INSERT INTO cash_register (name, branch_id) VALUES (?, ?) RETURNING id",
                                Long.class, "Caja 2", branchId);
        }

        /**
         * Abre un turno y desplaza su {@code opened_at} {@code minutesAgo} minutos
         * hacia atrás para controlar el orden del día en la prueba. {@code branch}
         * puede ser null (turno legado sin sucursal).
         */
        private long openShift(String by, long register, Long branch, int minutesAgo) {
                long id = shiftService.openShift(register, by, BigDecimal.ZERO, branch, null, null, false);
                if (minutesAgo != 0) {
                        jdbc().update("UPDATE shift SET opened_at = now() + (? || ' minutes')::interval WHERE id = ?",
                                        String.valueOf(minutesAgo), id);
                }
                return id;
        }

        private long setupBakeryProduct(String name) {
                Product p = catalogService.createProduct(new Product(
                                null, null, name, null, "pieza", false, null, null,
                                new BigDecimal("8"), BigDecimal.ZERO, true, List.of(), Map.of()));
                jdbc().update("UPDATE product SET waste_price = 3 WHERE id = ?", p.id());
                return p.id();
        }

        private BakeryProduction production(long productId, BigDecimal qty, BigDecimal entryPrice) {
                BakeryProductionLine line = new BakeryProductionLine(
                                null, productId, "pan", qty, "pieza", entryPrice, BigDecimal.ZERO,
                                entryPrice.multiply(qty));
                return new BakeryProduction(null, null, branchId, "PRODUCED", null,
                                qty, entryPrice.multiply(qty), null, "panadero", "CASHIER", null, List.of(line));
        }
}
