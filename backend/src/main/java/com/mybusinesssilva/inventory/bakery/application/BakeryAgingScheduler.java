package com.mybusinesssilva.inventory.bakery.application;

import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.BusinessRepository;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import java.text.Normalizer;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada que ejecuta el ENVEJECIMIENTO de pan (día anterior → merma)
 * de TODOS los negocios de giro panadería, una vez por noche.
 *
 * <p>
 * Por cada negocio del giro panadería fija el {@link TenantContext} (para que la
 * RLS aísle su schema) y, por cada sucursal activa, invoca
 * {@link BakeryUseCase#ageLeftoverToWaste(long, String)}. La guardia idempotente
 * {@code bakery_aging_run} evita envejecer dos veces el mismo día aunque el
 * catch-up de apertura de turno ya haya corrido. El contexto SIEMPRE se limpia en
 * {@code finally}, y un fallo por sucursal se registra sin abortar al resto.
 *
 * <p>
 * Imita el patrón de {@code TrialExpirationScheduler} (iterar
 * {@code businessRepository.findAll()} con cron configurable), pero a diferencia
 * de aquél, este SÍ fija y limpia el {@link TenantContext} por tenant.
 */
@Component
public class BakeryAgingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BakeryAgingScheduler.class);

    private final BusinessRepository businessRepository;
    private final BakeryUseCase bakeryUseCase;

    public BakeryAgingScheduler(BusinessRepository businessRepository, BakeryUseCase bakeryUseCase) {
        this.businessRepository = businessRepository;
        this.bakeryUseCase = bakeryUseCase;
    }

    /**
     * Corre cada noche (00:05 America/Mexico_City por defecto): envejece el pan del
     * día anterior de cada negocio de panadería, sucursal por sucursal.
     */
    @Scheduled(cron = "${app.bakery.aging-cron:0 5 0 * * *}", zone = "America/Mexico_City")
    public void ageAll() {
        for (Business b : businessRepository.findAll()) {
            if (!esGiroPanaderia(b)) {
                continue;
            }
            try {
                TenantContext.setTenantId(b.getSchemaName());
                for (Long branchId : bakeryUseCase.activeBranchIds()) {
                    try {
                        bakeryUseCase.ageLeftoverToWaste(branchId, "SISTEMA");
                    } catch (Exception e) {
                        log.error("Envejecimiento fallo tenant={} branch={}",
                                b.getSchemaName(), branchId, e);
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * @return {@code true} si el giro del negocio, normalizado (sin acentos, en
     *         minúsculas), es exactamente {@code "panaderia"}.
     */
    private boolean esGiroPanaderia(Business b) {
        String line = b.getBusinessLine();
        if (line == null) {
            return false;
        }
        String normalized = Normalizer.normalize(line, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return "panaderia".equals(normalized);
    }
}
