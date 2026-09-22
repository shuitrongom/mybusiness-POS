package com.mybusinesssilva.licensing.application;

import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.model.BusinessModule;
import com.mybusinesssilva.licensing.domain.port.out.BusinessModuleRepository;
import com.mybusinesssilva.licensing.domain.port.out.BusinessRepository;
import com.mybusinesssilva.platform.audit.AuditService;
import com.mybusinesssilva.platform.tenancy.TenantProvisioningService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de licenciamiento: alta de negocios con plan y prueba, compra de licencia
 * definitiva, venta de módulos adicionales (excedente), suspensión/reactivación y expiración
 * de pruebas.
 *
 * <p>Orquesta el dominio con los puertos de salida (persistencia, aprovisionamiento de tenant,
 * auditoría), manteniendo las reglas de negocio en la entidad {@link Business}.
 */
@Service
public class LicensingService {

    private final BusinessRegistrar businessRegistrar;
    private final BusinessRepository businessRepository;
    private final BusinessModuleRepository businessModuleRepository;
    private final TenantProvisioningService provisioningService;
    private final BusinessOwnerProvisioner ownerProvisioner;
    private final AuditService auditService;
    private final Clock clock;

    public LicensingService(BusinessRegistrar businessRegistrar,
                            BusinessRepository businessRepository,
                            BusinessModuleRepository businessModuleRepository,
                            TenantProvisioningService provisioningService,
                            BusinessOwnerProvisioner ownerProvisioner,
                            AuditService auditService,
                            Clock clock) {
        this.businessRegistrar = businessRegistrar;
        this.businessRepository = businessRepository;
        this.businessModuleRepository = businessModuleRepository;
        this.provisioningService = provisioningService;
        this.ownerProvisioner = ownerProvisioner;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Crea un negocio nuevo: valida el plan, lo persiste en prueba, habilita los módulos del plan
     * y aprovisiona su schema de datos. Registra la acción en auditoría.
     *
     * <p>Importante: el aprovisionamiento del schema del tenant (DDL vía Flyway) se realiza
     * FUERA de la transacción del alta de datos. Ejecutar DDL/Flyway dentro de la misma
     * transacción que mantiene bloqueos sobre las tablas de {@code admin} provoca interbloqueos.
     * Por eso el registro de datos y el aprovisionamiento se orquestan en pasos separados.
     *
     * <p>Además, tras aprovisionar el schema, crea el usuario Dueño (rol OWNER) con una
     * contraseña generada aleatoriamente. Las credenciales se devuelven UNA sola vez para que
     * el Super Admin las entregue al cliente (en la base solo queda el hash Argon2id).
     *
     * @param actor        quién realiza el alta (Super Admin)
     * @param name         nombre del negocio
     * @param rfc          RFC (puede ser nulo)
     * @param businessLine giro
     * @param planId       plan seleccionado
     * @param trialMonths  meses de prueba (configurable, 0..N)
     * @param ownerEmail   correo del dueño (acceso al negocio)
     * @param ownerName    nombre del dueño
     * @return el negocio creado junto con las credenciales del dueño
     */
    public CreateBusinessResult createBusiness(String actor, String name, String rfc,
                                               String businessLine, long planId, int trialMonths,
                                               String ownerEmail, String ownerName) {
        // Paso 1 (transaccional, en bean aparte): registra el negocio y habilita los módulos del plan.
        Business business = businessRegistrar.register(actor, name, rfc, businessLine, planId, trialMonths);

        // Paso 2 (fuera de la transacción anterior): aprovisiona el schema del tenant (DDL/Flyway).
        provisioningService.provisionSchema(business.getSchemaName());

        // Paso 3: crea el usuario dueño dentro del schema del tenant y obtiene sus credenciales.
        BusinessOwnerProvisioner.OwnerCredentials credentials =
                ownerProvisioner.createOwner(business.getSchemaName(), ownerEmail, ownerName);

        auditService.recordGlobal(actor, "BUSINESS_OWNER_CREATED", "business",
                String.valueOf(business.getId()), Map.of("ownerEmail", ownerEmail));

        return new CreateBusinessResult(business, credentials);
    }

    /**
     * Sobrecarga de conveniencia que crea el negocio y su usuario dueño con datos derivados
     * automáticamente (correo {@code owner@tenant_<id>.local}). Devuelve solo la entidad
     * {@link Business}. Pensada para pruebas y para flujos internos donde no se capturan
     * datos del dueño; el alta desde el panel del Super Admin usa la firma completa.
     */
    public Business createBusiness(String actor, String name, String rfc, String businessLine,
                                   long planId, int trialMonths) {
        Business business = businessRegistrar.register(actor, name, rfc, businessLine, planId, trialMonths);
        provisioningService.provisionSchema(business.getSchemaName());
        ownerProvisioner.createOwner(business.getSchemaName(),
                "owner@" + business.getSchemaName() + ".local", "Dueño");
        return business;
    }

    /**
     * Elimina definitivamente un negocio: borra su schema de datos (irreversible) y su registro
     * en el schema {@code admin}. Se recomienda respaldar antes con {@code exportBusiness}.
     */
    public void deleteBusiness(String actor, long businessId) {
        Business business = requireBusiness(businessId);
        String schema = business.getSchemaName();

        // 1) Elimina el schema del tenant y todos sus datos.
        if (schema != null && !schema.isBlank()) {
            provisioningService.dropSchema(schema);
        }
        // 2) Elimina el registro del negocio (y sus módulos/ventas) del schema admin.
        businessRepository.delete(businessId);

        auditService.recordGlobal(actor, "BUSINESS_DELETED", "business",
                String.valueOf(businessId), Map.of("name", business.getName(), "schema",
                        schema == null ? "" : schema));
    }

    /** Devuelve el negocio junto con sus módulos habilitados (para la vista de detalle). */
    public BusinessDetail businessDetail(long businessId) {
        Business business = requireBusiness(businessId);
        List<BusinessModule> modules = businessModuleRepository.findByBusinessId(businessId);
        return new BusinessDetail(business, modules);
    }

    /**
     * Resultado del alta de un negocio: la entidad creada y las credenciales del dueño
     * (contraseña en claro para mostrar una sola vez).
     */
    public record CreateBusinessResult(Business business,
                                       BusinessOwnerProvisioner.OwnerCredentials ownerCredentials) {
    }

    /** Detalle de un negocio: entidad y sus módulos habilitados. */
    public record BusinessDetail(Business business, List<BusinessModule> modules) {
    }

    /**
     * Registra la compra de la licencia definitiva de un negocio.
     */
    @Transactional
    public void purchaseLicense(String actor, long businessId) {
        Business business = requireBusiness(businessId);
        business.purchaseLicense(clock);
        businessRepository.update(business);
        auditService.recordGlobal(actor, "LICENSE_PURCHASED", "business",
                String.valueOf(businessId), Map.of());
    }

    /**
     * Vende un módulo adicional (excedente) a un negocio existente y lo habilita.
     */
    @Transactional
    public void sellSurchargeModule(String actor, long businessId, String moduleKey, BigDecimal price) {
        requireBusiness(businessId);
        businessModuleRepository.enableSurchargeModule(businessId, moduleKey, price);
        auditService.recordGlobal(actor, "MODULE_SOLD", "business",
                String.valueOf(businessId), Map.of("module", moduleKey, "price", price));
    }

    /**
     * Deshabilita un módulo de un negocio (conservando sus datos).
     */
    @Transactional
    public void disableModule(String actor, long businessId, String moduleKey) {
        requireBusiness(businessId);
        businessModuleRepository.disableModule(businessId, moduleKey);
        auditService.recordGlobal(actor, "MODULE_DISABLED", "business",
                String.valueOf(businessId), Map.of("module", moduleKey));
    }

    /** Suspende un negocio. */
    @Transactional
    public void suspend(String actor, long businessId) {
        Business business = requireBusiness(businessId);
        business.suspend();
        businessRepository.update(business);
        auditService.recordGlobal(actor, "BUSINESS_SUSPENDED", "business",
                String.valueOf(businessId), Map.of());
    }

    /** Reactiva un negocio suspendido. */
    @Transactional
    public void reactivate(String actor, long businessId) {
        Business business = requireBusiness(businessId);
        business.reactivate();
        businessRepository.update(business);
        auditService.recordGlobal(actor, "BUSINESS_REACTIVATED", "business",
                String.valueOf(businessId), Map.of());
    }

    /**
     * Recorre las pruebas vencidas y las marca como expiradas (bloqueo de acceso).
     *
     * @return número de negocios expirados en esta ejecución
     */
    @Transactional
    public int expireDueTrials() {
        List<Business> due = businessRepository.findTrialsDueForExpiration();
        int count = 0;
        for (Business business : due) {
            if (business.expireTrialIfDue(clock)) {
                businessRepository.update(business);
                auditService.recordGlobal("system", "TRIAL_EXPIRED", "business",
                        String.valueOf(business.getId()), Map.of());
                count++;
            }
        }
        return count;
    }

    public List<Business> listBusinesses() {
        return businessRepository.findAll();
    }

    public List<String> enabledModules(long businessId) {
        return businessModuleRepository.findEnabledModuleKeys(businessId);
    }

    private Business requireBusiness(long businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio inexistente: " + businessId));
    }
}
