package com.mybusinesssilva.licensing.adapters.in.rest;

import com.mybusinesssilva.licensing.domain.model.Plan;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel del Super Admin: catálogo de planes disponibles (con precios sugeridos y módulos).
 */
@RestController
@RequestMapping("/api/v1/admin/plans")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminPlanController {

    private final PlanRepository planRepository;

    public SuperAdminPlanController(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @GetMapping
    public List<Plan> listActivePlans() {
        return planRepository.findAllActive();
    }
}
