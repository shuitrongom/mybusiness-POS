package com.mybusinesssilva.platform.tenancy;

import java.util.List;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.BusinessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Al arrancar la aplicación, aplica las migraciones de tenant pendientes a TODOS los negocios
 * ya dados de alta.
 *
 * <p>Motivo (solución de raíz): las migraciones del tenant solo se ejecutaban al aprovisionar un
 * negocio nuevo ({@link TenantProvisioningService#provisionSchema}). Cuando se añade una
 * migración nueva (por ejemplo V23), los schemas de los tenants existentes se quedaban en la
 * versión anterior y el backend fallaba al consultar columnas nuevas. Este runner recorre cada
 * schema existente y ejecuta el mismo aprovisionamiento idempotente de Flyway, de modo que las
 * versiones pendientes se aplican solas en cada arranque.
 *
 * <p>Es idempotente: si un schema ya está en la última versión, Flyway no hace nada. El fallo de
 * un tenant no aborta el arranque ni impide migrar a los demás; se registra y se continúa.
 */
@Component
@Order(100)
public class TenantMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);

    private final BusinessRepository businessRepository;
    private final TenantProvisioningService provisioningService;

    public TenantMigrationRunner(BusinessRepository businessRepository,
                                 TenantProvisioningService provisioningService) {
        this.businessRepository = businessRepository;
        this.provisioningService = provisioningService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Business> businesses = businessRepository.findAll();
        if (businesses.isEmpty()) {
            log.info("[tenant-migrate] No hay negocios registrados; nada que migrar.");
            return;
        }

        int migrated = 0;
        int failed = 0;
        for (Business business : businesses) {
            String schema = business.getSchemaName();
            if (schema == null || schema.isBlank()) {
                continue;
            }
            try {
                provisioningService.provisionSchema(schema);
                migrated++;
                log.info("[tenant-migrate] Migraciones aplicadas al schema '{}' (negocio '{}').",
                        schema, business.getName());
            } catch (RuntimeException ex) {
                failed++;
                log.error("[tenant-migrate] Error al migrar el schema '{}' (negocio '{}'): {}",
                        schema, business.getName(), ex.getMessage(), ex);
            }
        }
        log.info("[tenant-migrate] Migración de tenants completada. OK={}, con error={}, total={}.",
                migrated, failed, businesses.size());
    }
}
