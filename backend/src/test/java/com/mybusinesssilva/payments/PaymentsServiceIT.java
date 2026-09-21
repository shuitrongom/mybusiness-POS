package com.mybusinesssilva.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.payments.application.PaymentsService;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integración de recargas y pago de servicios (con el agregador simulado):
 * operación exitosa con comisión, operación fallida sin comisión, y total de comisiones.
 */
class PaymentsServiceIT extends AbstractIntegrationTest {

    @Autowired
    private LicensingService licensingService;
    @Autowired
    private PaymentsService paymentsService;
    @Autowired
    private PlanRepository planRepository;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private void setup() {
        long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio Recargas", null, "abarrotes", planId, 1);
        TenantContext.setTenantId(business.getSchemaName());
    }

    @Test
    void rechargeSucceedsAndEarnsCommission() {
        setup();
        PaymentsService.OperationResult result = paymentsService.sellRecharge(
                "Telcel", "8110000000", new BigDecimal("100.00"), null, "cajero");

        assertThat(result.success()).isTrue();
        assertThat(result.folio()).isNotBlank();
        // Comisión de ejemplo 3% de 100 = 3.00
        assertThat(result.commission()).isEqualByComparingTo("3.00");
    }

    @Test
    void servicePaymentSucceedsAndEarnsCommission() {
        setup();
        PaymentsService.OperationResult result = paymentsService.payService(
                "CFE", "REC-123456", new BigDecimal("500.00"), null, "cajero");

        assertThat(result.success()).isTrue();
        // Comisión de ejemplo 2% de 500 = 10.00
        assertThat(result.commission()).isEqualByComparingTo("10.00");
    }

    @Test
    void failedOperationEarnsNoCommission() {
        setup();
        // Teléfono en blanco -> el agregador simulado devuelve fallo.
        PaymentsService.OperationResult result = paymentsService.sellRecharge(
                "Telcel", "", new BigDecimal("50.00"), null, "cajero");

        assertThat(result.success()).isFalse();
        assertThat(result.commission()).isEqualByComparingTo("0");
        assertThat(result.error()).isNotBlank();
    }

    @Test
    void totalCommissionsAccumulatesOnlySuccesses() {
        setup();
        paymentsService.sellRecharge("Telcel", "8110000001", new BigDecimal("100.00"), null, "c");
        paymentsService.payService("CFE", "REC-1", new BigDecimal("500.00"), null, "c");
        paymentsService.sellRecharge("Telcel", "", new BigDecimal("100.00"), null, "c"); // falla

        // 3.00 (recarga) + 10.00 (servicio) = 13.00, la fallida no suma.
        assertThat(paymentsService.totalCommissions()).isEqualByComparingTo("13.00");
    }
}
