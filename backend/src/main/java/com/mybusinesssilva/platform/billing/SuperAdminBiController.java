package com.mybusinesssilva.platform.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Business Intelligence del Super Admin (dueños del SaaS): métricas del negocio de MyBusiness Silva
 * como proveedor — cuántos negocios hay, por estado, e ingresos por venta de licencias y módulos.
 *
 * <p>No expone datos operativos de los negocios (ventas, inventario, etc.): eso pertenece a cada
 * negocio. Aquí solo se mide el desempeño del SaaS. Requiere rol SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/bi")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminBiController {

    private final JdbcClient jdbc;

    public SuperAdminBiController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Resumen general del SaaS: totales de negocios por estado e ingresos. */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        long total = count("SELECT count(*) FROM admin.business");
        long trial = count("SELECT count(*) FROM admin.business WHERE status = 'TRIAL'");
        long active = count("SELECT count(*) FROM admin.business WHERE status = 'ACTIVE'");
        long suspended = count("SELECT count(*) FROM admin.business WHERE status = 'SUSPENDED'");
        long expired = count("SELECT count(*) FROM admin.business WHERE status = 'EXPIRED'");

        BigDecimal totalRevenue = jdbc.sql(
                "SELECT COALESCE(SUM(amount),0) FROM admin.superadmin_sale")
                .query(BigDecimal.class).single();
        BigDecimal licenseRevenue = jdbc.sql(
                "SELECT COALESCE(SUM(amount),0) FROM admin.superadmin_sale WHERE kind = 'LICENSE'")
                .query(BigDecimal.class).single();
        BigDecimal surchargeRevenue = jdbc.sql(
                "SELECT COALESCE(SUM(amount),0) FROM admin.superadmin_sale WHERE kind = 'SURCHARGE'")
                .query(BigDecimal.class).single();
        long salesCount = count("SELECT count(*) FROM admin.superadmin_sale");

        return Map.of(
                "totalBusinesses", total,
                "trial", trial,
                "active", active,
                "suspended", suspended,
                "expired", expired,
                "totalRevenue", totalRevenue,
                "licenseRevenue", licenseRevenue,
                "surchargeRevenue", surchargeRevenue,
                "salesCount", salesCount);
    }

    /** Distribución de negocios por plan contratado. */
    @GetMapping("/by-plan")
    public List<Map<String, Object>> byPlan() {
        return jdbc.sql("""
                SELECT p.name AS plan, count(b.id) AS negocios
                FROM admin.plan p
                LEFT JOIN admin.business b ON b.plan_id = p.id
                GROUP BY p.name
                ORDER BY negocios DESC
                """)
                .query((rs, n) -> Map.<String, Object>of(
                        "plan", rs.getString("plan"),
                        "businesses", rs.getLong("negocios")))
                .list();
    }

    /** Últimas ventas del Super Admin (licencias y excedentes). */
    @GetMapping("/recent-sales")
    public List<Map<String, Object>> recentSales() {
        return jdbc.sql("""
                SELECT s.id, b.name AS business, s.kind, s.amount, s.voucher_type, s.created_at
                FROM admin.superadmin_sale s
                JOIN admin.business b ON b.id = s.business_id
                ORDER BY s.created_at DESC
                LIMIT 20
                """)
                .query((rs, n) -> Map.<String, Object>of(
                        "id", rs.getLong("id"),
                        "business", rs.getString("business"),
                        "kind", rs.getString("kind"),
                        "amount", rs.getBigDecimal("amount"),
                        "voucherType", rs.getString("voucher_type"),
                        "date", rs.getTimestamp("created_at").toInstant().toString()))
                .list();
    }

    private long count(String sql) {
        Long v = jdbc.sql(sql).query(Long.class).single();
        return v == null ? 0 : v;
    }
}
