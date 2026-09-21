package com.mybusinesssilva.licensing.application;

import com.mybusinesssilva.licensing.domain.model.Plan;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import com.mybusinesssilva.platform.audit.AuditService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de gestión de planes por el Super Admin (sin necesidad de programar):
 * crear, editar, duplicar y desactivar planes con sus módulos y precio sugerido.
 */
@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final AuditService auditService;

    public PlanService(PlanRepository planRepository, AuditService auditService) {
        this.planRepository = planRepository;
        this.auditService = auditService;
    }

    public List<Plan> listActive() {
        return planRepository.findAllActive();
    }

    /** Crea un plan nuevo con sus módulos y precio sugerido. */
    @Transactional
    public Plan create(String actor, String code, String name, String description,
                       BigDecimal licensePrice, List<String> moduleKeys) {
        planRepository.findByCode(code).ifPresent(p -> {
            throw new IllegalArgumentException("Ya existe un plan con el código " + code);
        });
        Plan plan = new Plan(null, code, name, description, licensePrice, true, moduleKeys);
        Plan saved = planRepository.insert(plan);
        auditService.recordGlobal(actor, "PLAN_CREATED", "plan", code,
                Map.of("name", name, "price", licensePrice, "modules", moduleKeys.size()));
        return saved;
    }

    /** Edita un plan existente (nombre, descripción, precio, módulos, activo). */
    @Transactional
    public Plan update(String actor, long id, String name, String description,
                       BigDecimal licensePrice, boolean active, List<String> moduleKeys) {
        Plan existing = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan inexistente: " + id));
        Plan updated = new Plan(existing.id(), existing.code(), name, description,
                licensePrice, active, moduleKeys);
        planRepository.update(updated);
        auditService.recordGlobal(actor, "PLAN_UPDATED", "plan", existing.code(), Map.of());
        return planRepository.findById(id).orElseThrow();
    }

    /** Duplica un plan como base para uno nuevo, con un código distinto. */
    @Transactional
    public Plan duplicate(String actor, long id, String newCode, String newName) {
        Plan source = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan inexistente: " + id));
        return create(actor, newCode, newName, source.description(),
                source.licensePriceSuggested(), source.moduleKeys());
    }

    /** Desactiva un plan (deja de ofrecerse sin borrar el histórico). */
    @Transactional
    public void deactivate(String actor, long id) {
        Plan existing = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan inexistente: " + id));
        Plan updated = new Plan(existing.id(), existing.code(), existing.name(),
                existing.description(), existing.licensePriceSuggested(), false, existing.moduleKeys());
        planRepository.update(updated);
        auditService.recordGlobal(actor, "PLAN_DEACTIVATED", "plan", existing.code(), Map.of());
    }
}
