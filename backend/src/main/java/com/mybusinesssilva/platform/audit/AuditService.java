package com.mybusinesssilva.platform.audit;

import java.util.Map;
import javax.sql.DataSource;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Registra acciones sensibles en la bitácora de auditoría global ({@code admin.audit_log_global}).
 *
 * <p>La auditoría es de solo inserción (append-only). No expone operaciones de actualización
 * ni borrado, y a nivel de base los usuarios operativos no tienen permiso para alterarla.
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    /**
     * Registra un evento de auditoría global.
     *
     * @param actor      quién realizó la acción (correo o id), puede ser nulo si es del sistema
     * @param action     nombre de la acción (por ejemplo {@code BUSINESS_CREATED})
     * @param targetType tipo de objeto afectado (por ejemplo {@code business})
     * @param targetId   identificador del objeto afectado
     * @param details    datos adicionales serializados como JSON
     */
    public void recordGlobal(String actor, String action, String targetType,
                             String targetId, Map<String, ?> details) {
        String detailsJson = toJson(details);
        // El literal JSON se castea a jsonb en la propia sentencia, sin acoplar al driver.
        jdbcTemplate.update(
                "INSERT INTO admin.audit_log_global (actor, action, target_type, target_id, details) "
                        + "VALUES (?, ?, ?, ?, CAST(? AS jsonb))",
                actor, action, targetType, targetId, detailsJson);
    }

    /**
     * Registra un evento de auditoría en la bitácora del TENANT ({@code <schema>.audit_log}).
     * Se usa para eventos operativos sensibles (apertura/cierre de caja, venta bloqueada, reintento
     * de reapertura). Append-only. La tabla se escribe calificada por schema y bajo el contexto de
     * tenant para satisfacer la RLS.
     *
     * @param schema     schema del tenant (por ejemplo {@code tenant_1})
     * @param actor      quién realizó la acción
     * @param action     nombre de la acción (SHIFT_OPENED, SHIFT_CLOSED, SALE_BLOCKED_CLOSED_SHIFT...)
     * @param targetType tipo de objeto (shift, sale...)
     * @param targetId   identificador del objeto
     * @param details    detalle adicional
     */
    public void recordTenant(String schema, String actor, String action, String targetType,
                             String targetId, Map<String, ?> details) {
        if (schema == null || schema.isBlank()) {
            return;
        }
        // Validación básica del nombre de schema para evitar inyección al calificar la tabla.
        if (!schema.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Nombre de schema inválido: " + schema);
        }
        String detailsJson = toJson(details);
        // Fija app.current_tenant en la misma conexión para pasar la RLS del audit_log del tenant.
        jdbcTemplate.execute((java.sql.Connection conn) -> {
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute("SET app.current_tenant = '" + schema.replace("'", "''") + "'");
            }
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + schema + ".audit_log (actor, action, target_type, target_id, details) "
                            + "VALUES (?, ?, ?, ?, CAST(? AS jsonb))")) {
                ps.setString(1, actor);
                ps.setString(2, action);
                ps.setString(3, targetType);
                ps.setString(4, targetId);
                ps.setString(5, detailsJson);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private String toJson(Map<String, ?> details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el detalle de auditoría", e);
        }
    }
}
