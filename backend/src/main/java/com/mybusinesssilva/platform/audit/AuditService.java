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
