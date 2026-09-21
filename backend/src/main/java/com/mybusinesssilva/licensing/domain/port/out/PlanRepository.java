package com.mybusinesssilva.licensing.domain.port.out;

import com.mybusinesssilva.licensing.domain.model.Plan;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de planes y sus módulos.
 */
public interface PlanRepository {

    Optional<Plan> findById(long id);

    Optional<Plan> findByCode(String code);

    List<Plan> findAllActive();

    /** @return las claves de módulo incluidas en el plan dado. */
    List<String> findModuleKeysByPlanId(long planId);

    Plan insert(Plan plan);

    void update(Plan plan);

    /** Reemplaza el conjunto de módulos de un plan. */
    void replaceModules(long planId, List<String> moduleKeys);
}
