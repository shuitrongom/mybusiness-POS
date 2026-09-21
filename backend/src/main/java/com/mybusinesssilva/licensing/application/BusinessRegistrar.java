package com.mybusinesssilva.licensing.application;

import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.model.Plan;
import com.mybusinesssilva.licensing.domain.port.out.BusinessModuleRepository;
import com.mybusinesssilva.licensing.domain.port.out.BusinessRepository;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.audit.AuditService;
import com.mybusinesssilva.platform.tenancy.TenantSchema;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra los datos de un negocio nuevo (schema {@code admin}) en una única transacción,
 * sin ejecutar DDL. Se separa de {@link LicensingService} para que la anotación
 * {@code @Transactional} surta efecto (el proxy de Spring no intercepta llamadas internas
 * a métodos de la misma clase).
 *
 * <p>El aprovisionamiento del schema del tenant (DDL vía Flyway) lo realiza el orquestador
 * después de esta transacción, evitando interbloqueos entre la transacción de datos y el DDL.
 */
@Component
public class BusinessRegistrar {

    private final BusinessRepository businessRepository;
    private final PlanRepository planRepository;
    private final BusinessModuleRepository businessModuleRepository;
    private final AuditService auditService;
    private final Clock clock;

    public BusinessRegistrar(BusinessRepository businessRepository,
                             PlanRepository planRepository,
                             BusinessModuleRepository businessModuleRepository,
                             AuditService auditService,
                             Clock clock) {
        this.businessRepository = businessRepository;
        this.planRepository = planRepository;
        this.businessModuleRepository = businessModuleRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Inserta el negocio, le asigna su schema derivado del id y habilita los módulos del plan.
     *
     * @return el negocio persistido con su schema asignado
     */
    @Transactional
    public Business register(String actor, String name, String rfc, String businessLine,
                             long planId, int trialMonths) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan inexistente: " + planId));

        Business business = Business.createInTrial(name, rfc, businessLine, planId, trialMonths, clock);
        business = businessRepository.insert(business);

        String schema = TenantSchema.forBusinessId(business.getId());
        business.assignSchema(schema);
        businessRepository.update(business);

        businessModuleRepository.enableModulesFromPlan(business.getId(), plan.moduleKeys());

        auditService.recordGlobal(actor, "BUSINESS_CREATED", "business",
                String.valueOf(business.getId()),
                Map.of("name", name, "plan", plan.code(), "trialMonths", trialMonths));

        return business;
    }
}
