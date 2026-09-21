package com.mybusinesssilva.licensing;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.model.BusinessStatus;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

/**
 * Pruebas de integración del flujo de licenciamiento (modelo de negocio real):
 * alta de negocio con plan y prueba, aprovisionamiento de su schema, habilitación de módulos,
 * venta de módulo adicional y compra de licencia definitiva.
 */
class LicensingServiceIT extends AbstractIntegrationTest {

    @Autowired
    private LicensingService licensingService;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void createsBusinessInTrialProvisionsSchemaAndEnablesPlanModules() {
        long professionalPlanId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();

        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com",
                "Abarrotes Don Silva", "SIL010101AAA", "abarrotes",
                professionalPlanId, 1);

        assertThat(business.getId()).isNotNull();
        assertThat(business.getStatus()).isEqualTo(BusinessStatus.TRIAL);
        assertThat(business.getSchemaName()).isEqualTo("tenant_" + business.getId());

        // El schema del tenant fue aprovisionado (existe la tabla branch con su RLS).
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer branchTableExists = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_name = 'branch'",
                Integer.class, business.getSchemaName());
        assertThat(branchTableExists).isEqualTo(1);

        // Los módulos del plan Profesional quedaron habilitados para el negocio.
        List<String> modules = licensingService.enabledModules(business.getId());
        assertThat(modules).contains("sales", "inventory", "invoicing", "payments");
        assertThat(modules).doesNotContain("bi", "mobile"); // esos son del plan Empresarial
    }

    @Test
    void trialWithZeroMonthsStartsExpired() {
        long essentialPlanId = planRepository.findByCode("ESSENTIAL").orElseThrow().id();

        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com",
                "Prueba Cero", null, "abarrotes", essentialPlanId, 0);

        assertThat(business.getStatus()).isEqualTo(BusinessStatus.EXPIRED);
    }

    @Test
    void purchaseLicenseActivatesBusiness() {
        long essentialPlanId = planRepository.findByCode("ESSENTIAL").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com",
                "Panadería La Silva", null, "panaderia", essentialPlanId, 1);

        licensingService.purchaseLicense("admin@mybusinesssilva.com", business.getId());

        Business reloaded = licensingService.listBusinesses().stream()
                .filter(b -> b.getId().equals(business.getId()))
                .findFirst().orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BusinessStatus.ACTIVE);
        assertThat(reloaded.getPurchasedAt()).isNotNull();
    }

    @Test
    void sellsSurchargeModuleToExistingBusiness() {
        long essentialPlanId = planRepository.findByCode("ESSENTIAL").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com",
                "Pollería El Silva", null, "polleria", essentialPlanId, 1);

        // Esencial no incluye BI; se vende como excedente.
        assertThat(licensingService.enabledModules(business.getId())).doesNotContain("bi");

        licensingService.sellSurchargeModule(
                "admin@mybusinesssilva.com", business.getId(), "bi", new BigDecimal("3500.00"));

        assertThat(licensingService.enabledModules(business.getId())).contains("bi");
    }
}
