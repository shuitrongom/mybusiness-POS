package com.mybusinesssilva.licensing.adapters.out.persistence;

import com.mybusinesssilva.licensing.domain.model.BusinessModule;
import com.mybusinesssilva.licensing.domain.model.BusinessModule.ModuleOrigin;
import com.mybusinesssilva.licensing.domain.port.out.BusinessModuleRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC de los módulos habilitados por negocio, sobre el schema {@code admin}.
 */
@Repository
public class JdbcBusinessModuleRepository implements BusinessModuleRepository {

    private final JdbcClient jdbc;

    public JdbcBusinessModuleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enableModulesFromPlan(long businessId, List<String> moduleKeys) {
        for (String key : moduleKeys) {
            jdbc.sql("""
                    INSERT INTO admin.business_module (business_id, module_key, enabled, origin)
                    VALUES (:bid, :key, true, 'PLAN')
                    ON CONFLICT (business_id, module_key)
                    DO UPDATE SET enabled = true
                    """)
                    .param("bid", businessId)
                    .param("key", key)
                    .update();
        }
    }

    @Override
    public void enableSurchargeModule(long businessId, String moduleKey, BigDecimal soldPrice) {
        jdbc.sql("""
                INSERT INTO admin.business_module
                    (business_id, module_key, enabled, origin, sold_price)
                VALUES (:bid, :key, true, 'SURCHARGE', :price)
                ON CONFLICT (business_id, module_key)
                DO UPDATE SET enabled = true, origin = 'SURCHARGE', sold_price = :price
                """)
                .param("bid", businessId)
                .param("key", moduleKey)
                .param("price", soldPrice)
                .update();
    }

    @Override
    public void disableModule(long businessId, String moduleKey) {
        jdbc.sql("""
                UPDATE admin.business_module SET enabled = false
                WHERE business_id = :bid AND module_key = :key
                """)
                .param("bid", businessId)
                .param("key", moduleKey)
                .update();
    }

    @Override
    public List<String> findEnabledModuleKeys(long businessId) {
        return jdbc.sql("""
                SELECT module_key FROM admin.business_module
                WHERE business_id = :bid AND enabled = true ORDER BY module_key
                """)
                .param("bid", businessId)
                .query(String.class)
                .list();
    }

    @Override
    public List<BusinessModule> findByBusinessId(long businessId) {
        return jdbc.sql("""
                SELECT module_key, enabled, origin, sold_price, sold_at
                FROM admin.business_module WHERE business_id = :bid ORDER BY module_key
                """)
                .param("bid", businessId)
                .query((rs, rowNum) -> new BusinessModule(
                        rs.getString("module_key"),
                        rs.getBoolean("enabled"),
                        ModuleOrigin.valueOf(rs.getString("origin")),
                        rs.getBigDecimal("sold_price"),
                        rs.getTimestamp("sold_at") == null
                                ? null : rs.getTimestamp("sold_at").toInstant()))
                .list();
    }
}
