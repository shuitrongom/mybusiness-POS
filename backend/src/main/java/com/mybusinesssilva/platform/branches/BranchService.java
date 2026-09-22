package com.mybusinesssilva.platform.branches;

import com.mybusinesssilva.platform.audit.AuditService;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de sucursales del negocio (tenant). Opera sobre el schema del tenant en curso.
 *
 * <p>Un negocio arranca con la sucursal "Matriz" creada al aprovisionar. El Dueño/Administrador
 * puede dar de alta más sucursales; cada una tiene su propio inventario y sus ventas.
 */
@Service
public class BranchService {

    private final JdbcClient jdbc;
    private final AuditService auditService;

    public BranchService(JdbcClient jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    /** Lista las sucursales del negocio. */
    public List<Map<String, Object>> list() {
        return jdbc.sql("SELECT id, name, code, active FROM branch ORDER BY id")
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("code", rs.getString("code"));
                    m.put("active", rs.getBoolean("active"));
                    return m;
                })
                .list();
    }

    /** Crea una sucursal nueva y devuelve su id. */
    @Transactional
    public long create(String actor, String name, String code) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la sucursal es obligatorio");
        }
        long id = jdbc.sql("INSERT INTO branch (name, code, active) VALUES (:name, :code, TRUE) RETURNING id")
                .param("name", name.trim())
                .param("code", code == null || code.isBlank() ? null : code.trim())
                .query(Long.class)
                .single();
        auditService.recordGlobal(actor, "BRANCH_CREATED", "branch", String.valueOf(id),
                Map.of("name", name));
        return id;
    }

    /** Renombra o recodifica una sucursal existente. */
    @Transactional
    public void update(String actor, long id, String name, String code) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la sucursal es obligatorio");
        }
        jdbc.sql("UPDATE branch SET name = :name, code = :code WHERE id = :id")
                .param("id", id)
                .param("name", name.trim())
                .param("code", code == null || code.isBlank() ? null : code.trim())
                .update();
        auditService.recordGlobal(actor, "BRANCH_UPDATED", "branch", String.valueOf(id), Map.of());
    }

    /** Activa o desactiva una sucursal (una desactivada no debe usarse para vender). */
    @Transactional
    public void setActive(String actor, long id, boolean active) {
        // No permitir desactivar la última sucursal activa (el negocio quedaría sin dónde vender).
        if (!active) {
            long activos = jdbc.sql("SELECT count(*) FROM branch WHERE active = TRUE")
                    .query(Long.class).single();
            if (activos <= 1) {
                throw new IllegalStateException("Debe quedar al menos una sucursal activa");
            }
        }
        jdbc.sql("UPDATE branch SET active = :active WHERE id = :id")
                .param("active", active)
                .param("id", id)
                .update();
        auditService.recordGlobal(actor, active ? "BRANCH_ENABLED" : "BRANCH_DISABLED",
                "branch", String.valueOf(id), Map.of());
    }
}
