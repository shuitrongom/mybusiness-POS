package com.mybusinesssilva.invoicing;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.invoicing.application.InvoicingService;
import com.mybusinesssilva.invoicing.domain.model.CfdiConcept;
import com.mybusinesssilva.invoicing.domain.model.CfdiStatus;
import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integración de facturación CFDI 4.0 (con el PAC simulado):
 * emisión y timbrado, idempotencia (no re-timbrar) y cancelación.
 */
class InvoicingServiceIT extends AbstractIntegrationTest {

    @Autowired
    private LicensingService licensingService;
    @Autowired
    private InvoicingService invoicingService;
    @Autowired
    private PlanRepository planRepository;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private void setup() {
        long planId = planRepository.findByCode("PROFESSIONAL").orElseThrow().id();
        Business business = licensingService.createBusiness(
                "admin@mybusinesssilva.com", "Negocio CFDI", "SIL010101AAA", "abarrotes", planId, 1);
        TenantContext.setTenantId(business.getSchemaName());
    }

    private ReceiverInfo receiver() {
        return new ReceiverInfo("XAXX010101000", "Publico General", "64000", "616", "G03");
    }

    private List<CfdiConcept> concepts() {
        return List.of(new CfdiConcept("50200000", "H87", "Coca-Cola 600ml",
                BigDecimal.ONE, new BigDecimal("18.00"), new BigDecimal("18.00")));
    }

    @Test
    void issuesAndStampsInvoice() {
        setup();
        InvoicingService.InvoiceResult result =
                invoicingService.issueInvoice(null, receiver(), concepts(), "inv-001");

        assertThat(result.status()).isEqualTo(CfdiStatus.STAMPED);
        assertThat(result.uuid()).isNotBlank();
        assertThat(result.duplicated()).isFalse();
    }

    @Test
    void idempotentIssueDoesNotStampTwice() {
        setup();
        InvoicingService.InvoiceResult first =
                invoicingService.issueInvoice(null, receiver(), concepts(), "inv-002");
        InvoicingService.InvoiceResult second =
                invoicingService.issueInvoice(null, receiver(), concepts(), "inv-002");

        assertThat(second.duplicated()).isTrue();
        assertThat(second.cfdiId()).isEqualTo(first.cfdiId());
        assertThat(second.uuid()).isEqualTo(first.uuid());
    }

    @Test
    void cancelsStampedInvoice() {
        setup();
        InvoicingService.InvoiceResult result =
                invoicingService.issueInvoice(null, receiver(), concepts(), "inv-003");

        boolean canceled = invoicingService.cancelInvoice(result.cfdiId());
        assertThat(canceled).isTrue();
    }
}
