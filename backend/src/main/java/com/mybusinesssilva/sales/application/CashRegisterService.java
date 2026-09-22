package com.mybusinesssilva.sales.application;

import com.mybusinesssilva.platform.audit.AuditService;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de cajas registradoras del negocio. Cada caja pertenece a una sucursal; los turnos y
 * cortes de caja se abren sobre una caja concreta. Opera sobre el schema del tenant en curso.
 */
@Service
public class CashRegisterService {

    private final JdbcClient jdbc;
    private final AuditService auditService;

    public CashRegisterService(JdbcClient jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    /**
     * Lista las cajas registradoras, opcionalmente filtradas por sucursal.
     *
     * @param branchId sucursal para filtrar, o {@code null} para todas
     */
    public List<Map<String, Object>> list(Long branchId) {
        String sql = """
                SELECT c.id, c.name, c.active, c.branch_id, b.name AS branch_name
                FROM cash_register c
                JOIN branch b ON b.id = c.branch_id
                """ + (branchId == null ? "" : " WHERE c.branch_id = :branch ")
                + " ORDER BY c.branch_id, c.id";
        var spec = jdbc.sql(sql);
        if (branchId != null) {
            spec = spec.param("branch", branchId);
        }
        return spec.query((rs, n) -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("active", rs.getBoolean("active"));
            m.put("branchId", rs.getLong("branch_id"));
            m.put("branchName", rs.getString("branch_name"));
            return m;
        }).list();
    }

    /** Crea una caja registradora en una sucursal. */
    @Transactional
    public long create(String actor, long branchId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la caja es obligatorio");
        }
        long id = jdbc.sql("""
                INSERT INTO cash_register (branch_id, name, active) VALUES (:branch, :name, TRUE)
                RETURNING id
                """)
                .param("branch", branchId)
                .param("name", name.trim())
                .query(Long.class)
                .single();
        auditService.recordGlobal(actor, "CASH_REGISTER_CREATED", "cash_register",
                String.valueOf(id), Map.of("branchId", branchId, "name", name));
        return id;
    }

    /** Activa o desactiva una caja registradora. */
    @Transactional
    public void setActive(String actor, long id, boolean active) {
        jdbc.sql("UPDATE cash_register SET active = :active WHERE id = :id")
                .param("active", active)
                .param("id", id)
                .update();
        auditService.recordGlobal(actor, active ? "CASH_REGISTER_ENABLED" : "CASH_REGISTER_DISABLED",
                "cash_register", String.valueOf(id), Map.of());
    }
}
