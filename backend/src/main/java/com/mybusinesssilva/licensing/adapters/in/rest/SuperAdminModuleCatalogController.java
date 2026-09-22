package com.mybusinesssilva.licensing.adapters.in.rest;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de módulos comercializables, para poblar el editor de planes del Super Admin.
 * Solo lectura; los módulos disponibles se definen por migración en {@code admin.module_catalog}.
 */
@RestController
@RequestMapping("/api/v1/admin/modules")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminModuleCatalogController {

    private final JdbcClient jdbc;

    public SuperAdminModuleCatalogController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<ModuleView> list() {
        return jdbc.sql("""
                SELECT module_key, name, description, surcharge_suggested_price
                FROM admin.module_catalog
                ORDER BY name
                """)
                .query((rs, rowNum) -> new ModuleView(
                        rs.getString("module_key"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("surcharge_suggested_price")))
                .list();
    }

    /** Vista de un módulo del catálogo. */
    public record ModuleView(String moduleKey, String name, String description,
                             BigDecimal surchargeSuggestedPrice) {
    }
}
