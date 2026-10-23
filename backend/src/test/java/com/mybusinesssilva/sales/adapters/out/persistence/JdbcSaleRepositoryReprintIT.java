package com.mybusinesssilva.sales.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.sales.domain.port.out.SaleRepository;
import com.mybusinesssilva.sales.domain.port.out.SaleRepository.ReprintSale;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pruebas de integración de {@link SaleRepository#reprintList} (pantalla de
 * reimpresión de tickets). Ejecutan la consulta REAL contra la base de datos de
 * prueba para blindar:
 * <ul>
 * <li>Que el SQL es sintácticamente válido con scope {@code real} y {@code both}
 * (una concatenación pegada del fragmento de scope reventaría aquí — AC17).</li>
 * <li>El filtro de fecha por día natural en zona {@code America/Mexico_City},
 * incluido el caso borde ~23:59 hora local.</li>
 * <li>Los filtros de método/tipo y la búsqueda {@code q} por
 * folio/cajero/cliente/producto, sin duplicar filas de venta.</li>
 * <li>El scope de modo prueba: {@code real} oculta las de prueba; {@code both}
 * muestra ambas.</li>
 * </ul>
 */
class JdbcSaleRepositoryReprintIT extends AbstractIntegrationTest {

        @Autowired
        private SaleRepository saleRepository;
        @Autowired
        private LicensingService licensingService;
        @Autowired
        private PlanRepository planRepository;
        @Autowired
        private DataSource dataSource;

        private JdbcTemplate jdbc;
        private long branchId;
        private long otherBranchId;

        @AfterEach
        void clear() {
                TenantContext.clear();
        }

        @Test
        void listsRealOnlyWhenScopeRealAndBothWhenScopeBoth() {
                setupTenant();
                long customerId = insertCustomer("Juan Pérez");
                // Venta REAL con cliente y una concha.
                long realSale = insertSale(branchId, "cajera1", customerId, "NORMAL", false,
                                new BigDecimal("30.00"), new BigDecimal("50.00"));
                insertLine(realSale, "Concha", new BigDecimal("3"));
                insertPayment(realSale, "CASH", new BigDecimal("30.00"));
                // Venta de PRUEBA (is_test = true).
                long testSale = insertSale(branchId, "cajera1", null, "NORMAL", true,
                                new BigDecimal("10.00"), new BigDecimal("10.00"));
                insertLine(testSale, "Bolillo", new BigDecimal("10"));
                insertPayment(testSale, "CARD", new BigDecimal("10.00"));

                // scope=real: solo la real; el SQL debe ejecutar sin error de sintaxis (AC17).
                List<ReprintSale> real = saleRepository.reprintList(300, null, null, null, null, null,
                                null, "real");
                assertThat(real).extracting(ReprintSale::saleId).contains(realSale).doesNotContain(testSale);

                // scope=both: ambas (el SQL con fragmento vacío de scope también debe ejecutar).
                List<ReprintSale> both = saleRepository.reprintList(300, null, null, null, null, null,
                                null, "both");
                assertThat(both).extracting(ReprintSale::saleId).contains(realSale, testSale);
        }

        @Test
        void filtersByDateRangeInMexicoCityTimezoneIncludingMidnightEdge() {
                setupTenant();
                long sale = insertSale(branchId, "cajera1", null, "NORMAL", false,
                                new BigDecimal("12.00"), new BigDecimal("12.00"));
                insertLine(sale, "Telera", new BigDecimal("2"));
                // Fuerza created_at a las 23:59 hora de México de HOY (día natural de hoy).
                jdbc.update("UPDATE sale SET created_at = "
                                + "((now() AT TIME ZONE 'America/Mexico_City')::date + time '23:59') "
                                + "AT TIME ZONE 'America/Mexico_City' WHERE id = ?", sale);

                LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Mexico_City"));
                // Un solo día (from == to) captura el día natural completo, pese al borde 23:59.
                List<ReprintSale> inRange = saleRepository.reprintList(300, null, today, today, null,
                                null, null, "both");
                assertThat(inRange).extracting(ReprintSale::saleId).contains(sale);

                // Día anterior: no debe aparecer.
                List<ReprintSale> yesterday = saleRepository.reprintList(300, null,
                                today.minusDays(1), today.minusDays(1), null, null, null, "both");
                assertThat(yesterday).extracting(ReprintSale::saleId).doesNotContain(sale);
        }

        @Test
        void filtersByMethodTypeBranchAndSearchWithoutDuplicateRows() {
                setupTenant();
                long customerId = insertCustomer("María López");
                // Venta NORMAL, efectivo, con dos renglones (para probar q por producto sin duplicar).
                long normalSale = insertSale(branchId, "cajera2", customerId, "NORMAL", false,
                                new BigDecimal("45.00"), new BigDecimal("45.00"));
                insertLine(normalSale, "Concha", new BigDecimal("3"));
                insertLine(normalSale, "Concha integral", new BigDecimal("2"));
                insertPayment(normalSale, "CASH", new BigDecimal("45.00"));
                // Venta SPECIAL con tarjeta en otra sucursal.
                long specialSale = insertSale(otherBranchId, "cajera3", null, "SPECIAL", false,
                                new BigDecimal("80.00"), new BigDecimal("80.00"));
                insertLine(specialSale, "Pastel personalizado", new BigDecimal("1"));
                insertPayment(specialSale, "CARD", new BigDecimal("80.00"));

                // Método CASH → solo la normal.
                assertThat(saleRepository.reprintList(300, null, null, null, null, "CASH", null, "both"))
                                .extracting(ReprintSale::saleId).contains(normalSale).doesNotContain(specialSale);
                // Tipo SPECIAL → solo la especial.
                assertThat(saleRepository.reprintList(300, null, null, null, null, null, "SPECIAL", "both"))
                                .extracting(ReprintSale::saleId).contains(specialSale).doesNotContain(normalSale);
                // Sucursal → solo la de esa sucursal.
                assertThat(saleRepository.reprintList(300, null, null, null, otherBranchId, null, null, "both"))
                                .extracting(ReprintSale::saleId).contains(specialSale).doesNotContain(normalSale);
                // q por cliente.
                assertThat(saleRepository.reprintList(300, "María", null, null, null, null, null, "both"))
                                .extracting(ReprintSale::saleId).contains(normalSale);
                // q por cajero.
                assertThat(saleRepository.reprintList(300, "cajera3", null, null, null, null, null, "both"))
                                .extracting(ReprintSale::saleId).contains(specialSale);
                // q por producto: "Concha" matchea dos renglones de la MISMA venta → una sola fila.
                List<ReprintSale> byProduct = saleRepository.reprintList(300, "Concha", null, null, null,
                                null, null, "both");
                assertThat(byProduct).filteredOn(r -> r.saleId() == normalSale).hasSize(1);
                // El preview trae la cantidad con la descripción.
                assertThat(byProduct).filteredOn(r -> r.saleId() == normalSale).singleElement()
                                .satisfies(r -> {
                                        assertThat(r.itemsPreview()).contains("Concha x3");
                                        assertThat(r.itemCount()).isEqualTo(2);
                                        assertThat(r.customerName()).isEqualTo("María López");
                                        assertThat(r.paymentMethods()).contains("CASH");
                                        assertThat(r.saleType()).isEqualTo("NORMAL");
                                });
        }

        // --- helpers ---

        private void setupTenant() {
                long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
                Business business = licensingService.createBusiness(
                                "admin@mybusinesssilva.com", "Panaderia Reprint IT", null, "panaderia", planId, 1);
                TenantContext.setTenantId(business.getSchemaName());
                jdbc = new JdbcTemplate(dataSource);
                branchId = jdbc.queryForObject(
                                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, "Matriz");
                otherBranchId = jdbc.queryForObject(
                                "INSERT INTO branch (name) VALUES (?) RETURNING id", Long.class, "Sucursal 2");
        }

        private long insertCustomer(String name) {
                return jdbc.queryForObject(
                                "INSERT INTO customer (name) VALUES (?) RETURNING id", Long.class, name);
        }

        private long insertSale(long branch, String cashier, Long customerId, String saleType,
                        boolean isTest, BigDecimal total, BigDecimal amountPaid) {
                return jdbc.queryForObject(
                                "INSERT INTO sale (branch_id, cashier, customer_id, status, sale_type, "
                                                + "total, amount_paid, is_test) "
                                                + "VALUES (?, ?, ?, 'COMPLETED', ?, ?, ?, ?) RETURNING id",
                                Long.class, branch, cashier, customerId, saleType, total, amountPaid, isTest);
        }

        private void insertLine(long saleId, String description, BigDecimal quantity) {
                jdbc.update(
                                "INSERT INTO sale_line (sale_id, description, quantity, unit_price, line_total) "
                                                + "VALUES (?, ?, ?, 0, 0)",
                                saleId, description, quantity);
        }

        private void insertPayment(long saleId, String method, BigDecimal amount) {
                jdbc.update("INSERT INTO sale_payment (sale_id, method, amount) VALUES (?, ?, ?)",
                                saleId, method, amount);
        }
}
