package com.mybusinesssilva.licensing;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.bi.application.insight.Insight;
import com.mybusinesssilva.bi.application.insight.InsightEngine;
import com.mybusinesssilva.bi.domain.model.TimeSeriesPoint;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.billing.SuperAdminBiService;
import com.mybusinesssilva.platform.billing.TrialEnding;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pruebas de integración del BI de PLATAFORMA del Super Admin sobre el schema global {@code admin}:
 * se dan de alta negocios y se registran ventas de licencias y módulos, y se verifican el conteo
 * comercial y la mezcla de planes (sobre {@code admin}), las nuevas series mensuales y pruebas por
 * vencer ({@link SuperAdminBiService}, agrupadas en {@code America/Mexico_City}) y los insights,
 * reutilizando el mismo {@link InsightEngine} del BI de negocio.
 *
 * <p>Las consultas de resumen/mezcla/ventas recientes del {@code SuperAdminBiController} están
 * protegidas con {@code @PreAuthorize("hasRole('SUPER_ADMIN')")}; aquí se verifica su misma fuente
 * de datos (schema {@code admin}) por SQL directa, sin montar un SecurityContext, ya que el objeto
 * de esta prueba es el comportamiento de datos de FEAT-002.
 */
class SuperAdminBiIT extends AbstractIntegrationTest {

    @Autowired private LicensingService licensingService;
    @Autowired private SuperAdminBiService biService;
    @Autowired private InsightEngine insightEngine;
    @Autowired private PlanRepository planRepository;
    @Autowired private DataSource dataSource;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void platformBiReflectsBusinessesAndSales() {
        long planId = planRepository.findByCode("ENTERPRISE").orElseThrow().id();

        Business first = licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio Alfa", null, "abarrotes", planId, 1);
        Business second = licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio Beta", null, "panaderia", planId, 1);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // Ambos negocios quedan con su prueba por vencer dentro de los próximos 15 días.
        jdbc.update("UPDATE admin.business SET trial_ends_at = now() + interval '10 days' "
                + "WHERE id IN (?, ?)", first.getId(), second.getId());

        // Ventas del Super Admin: una licencia y un módulo adicional.
        insertSale(jdbc, first.getId(), "LICENSE", new BigDecimal("12000.00"), "CFDI");
        insertSale(jdbc, second.getId(), "SURCHARGE", new BigDecimal("3500.00"), "PDF");

        // Conteo comercial sobre el schema admin (misma fuente que el resumen del controlador).
        long totalBusinesses = jdbc.queryForObject(
                "SELECT count(*) FROM admin.business", Long.class);
        long trial = jdbc.queryForObject(
                "SELECT count(*) FROM admin.business WHERE status = 'TRIAL'", Long.class);
        long salesCount = jdbc.queryForObject(
                "SELECT count(*) FROM admin.superadmin_sale", Long.class);
        BigDecimal licenseRevenue = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM admin.superadmin_sale WHERE kind = 'LICENSE'",
                BigDecimal.class);
        BigDecimal surchargeRevenue = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM admin.superadmin_sale WHERE kind = 'SURCHARGE'",
                BigDecimal.class);
        assertThat(totalBusinesses).isGreaterThanOrEqualTo(2);
        assertThat(trial).isGreaterThanOrEqualTo(2);
        assertThat(salesCount).isGreaterThanOrEqualTo(2);
        assertThat(licenseRevenue).isGreaterThanOrEqualTo(new BigDecimal("12000.00"));
        assertThat(surchargeRevenue).isGreaterThanOrEqualTo(new BigDecimal("3500.00"));

        // Mezcla de planes: ambos negocios están en el plan Empresarial.
        long businessesInEnterprise = jdbc.queryForObject(
                "SELECT count(*) FROM admin.business WHERE plan_id = ?", Long.class, planId);
        assertThat(businessesInEnterprise).isGreaterThanOrEqualTo(2);

        // Serie mensual de ingresos: agrupa en formato yyyy-MM y suma el ingreso del mes.
        List<TimeSeriesPoint> revenue = biService.revenueByMonth(12);
        assertThat(revenue).isNotEmpty();
        assertThat(revenue.get(revenue.size() - 1).date()).matches("\\d{4}-\\d{2}");
        BigDecimal totalSeries = revenue.stream()
                .map(TimeSeriesPoint::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalSeries).isGreaterThanOrEqualTo(new BigDecimal("15500.00"));

        // Serie mensual de negocios creados.
        List<TimeSeriesPoint> created = biService.businessesByMonth(12);
        assertThat(created).isNotEmpty();
        assertThat(created.get(created.size() - 1).date()).matches("\\d{4}-\\d{2}");

        // Pruebas por vencer en 15 días: incluye a los dos negocios recién creados.
        List<TrialEnding> ending = biService.trialsEndingSoon(15);
        assertThat(ending).extracting(TrialEnding::businessId)
                .contains(first.getId(), second.getId());

        // Insights de plataforma: el mismo InsightEngine devuelve al menos uno con severidad válida.
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("series", revenue);
        List<Insight> insights = insightEngine.generate("plataforma", metrics);
        assertThat(insights).isNotEmpty();
        assertThat(insights).allMatch(i -> i.severidad() != null);
    }

    private void insertSale(JdbcTemplate jdbc, long businessId, String kind, BigDecimal amount,
            String voucherType) {
        jdbc.update("""
                INSERT INTO admin.superadmin_sale (business_id, kind, amount, voucher_type)
                VALUES (?, ?, ?, ?)
                """, businessId, kind, amount, voucherType);
    }
}
