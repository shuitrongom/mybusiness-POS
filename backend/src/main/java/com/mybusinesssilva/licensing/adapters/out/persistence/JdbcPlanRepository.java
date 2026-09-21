package com.mybusinesssilva.licensing.adapters.out.persistence;

import com.mybusinesssilva.licensing.domain.model.Plan;
import com.mybusinesssilva.licensing.domain.port.out.PlanRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del repositorio de planes sobre el schema {@code admin}.
 */
@Repository
public class JdbcPlanRepository implements PlanRepository {

    private final JdbcClient jdbc;

    public JdbcPlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Plan> findById(long id) {
        return loadPlan("SELECT * FROM admin.plan WHERE id = :id", "id", id);
    }

    @Override
    public Optional<Plan> findByCode(String code) {
        return loadPlan("SELECT * FROM admin.plan WHERE code = :code", "code", code);
    }

    @Override
    public List<Plan> findAllActive() {
        List<PlanRow> rows = jdbc.sql("SELECT * FROM admin.plan WHERE active = true ORDER BY id")
                .query(PlanRow.class)
                .list();
        return rows.stream()
                .map(r -> toPlan(r, findModuleKeysByPlanId(r.id())))
                .toList();
    }

    @Override
    public List<String> findModuleKeysByPlanId(long planId) {
        return jdbc.sql("SELECT module_key FROM admin.plan_module WHERE plan_id = :id ORDER BY module_key")
                .param("id", planId)
                .query(String.class)
                .list();
    }

    @Override
    public Plan insert(Plan plan) {
        Long id = jdbc.sql("""
                INSERT INTO admin.plan (code, name, description, license_price_suggested, active)
                VALUES (:code, :name, :desc, :price, :active)
                RETURNING id
                """)
                .param("code", plan.code())
                .param("name", plan.name())
                .param("desc", plan.description())
                .param("price", plan.licensePriceSuggested())
                .param("active", plan.active())
                .query(Long.class)
                .single();
        replaceModules(id, plan.moduleKeys());
        return findById(id).orElseThrow();
    }

    @Override
    public void update(Plan plan) {
        jdbc.sql("""
                UPDATE admin.plan SET name = :name, description = :desc,
                    license_price_suggested = :price, active = :active, updated_at = now()
                WHERE id = :id
                """)
                .param("id", plan.id())
                .param("name", plan.name())
                .param("desc", plan.description())
                .param("price", plan.licensePriceSuggested())
                .param("active", plan.active())
                .update();
        replaceModules(plan.id(), plan.moduleKeys());
    }

    @Override
    public void replaceModules(long planId, List<String> moduleKeys) {
        jdbc.sql("DELETE FROM admin.plan_module WHERE plan_id = :id").param("id", planId).update();
        for (String key : moduleKeys) {
            jdbc.sql("INSERT INTO admin.plan_module (plan_id, module_key) VALUES (:id, :key)")
                    .param("id", planId)
                    .param("key", key)
                    .update();
        }
    }

    private Optional<Plan> loadPlan(String sql, String paramName, Object paramValue) {
        Optional<PlanRow> row = jdbc.sql(sql)
                .param(paramName, paramValue)
                .query(PlanRow.class)
                .optional();
        return row.map(r -> toPlan(r, findModuleKeysByPlanId(r.id())));
    }

    private Plan toPlan(PlanRow r, List<String> moduleKeys) {
        return new Plan(r.id(), r.code(), r.name(), r.description(),
                r.licensePriceSuggested(), r.active(), moduleKeys);
    }

    /** Proyección de fila de la tabla plan. */
    public record PlanRow(
            Long id,
            String code,
            String name,
            String description,
            java.math.BigDecimal licensePriceSuggested,
            boolean active) {
    }
}
