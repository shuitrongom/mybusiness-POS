package com.mybusinesssilva.licensing.domain.port.out;

import com.mybusinesssilva.licensing.domain.model.BusinessModule;
import java.util.List;

/**
 * Puerto de salida para los módulos habilitados comercialmente por negocio.
 */
public interface BusinessModuleRepository {

    /** Habilita un conjunto de módulos para un negocio (origen PLAN). */
    void enableModulesFromPlan(long businessId, List<String> moduleKeys);

    /** Habilita un módulo adicional vendido como excedente. */
    void enableSurchargeModule(long businessId, String moduleKey, java.math.BigDecimal soldPrice);

    /** Deshabilita un módulo de un negocio (conservando sus datos). */
    void disableModule(long businessId, String moduleKey);

    /** @return las claves de módulo habilitadas para el negocio. */
    List<String> findEnabledModuleKeys(long businessId);

    List<BusinessModule> findByBusinessId(long businessId);
}
