package com.mybusinesssilva.notifications.adapters.out.persistence;

import com.mybusinesssilva.notifications.domain.model.Notification;
import com.mybusinesssilva.notifications.domain.model.RecipientScope;
import com.mybusinesssilva.notifications.domain.port.in.NotificationScope;
import com.mybusinesssilva.notifications.domain.port.out.NotificationRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Implementación JDBC de {@link NotificationRepository} sobre el schema
 * {@code admin}. Usa
 * {@link JdbcClient} con parámetros nombrados; el {@code payload} se serializa
 * a JSONB.
 */
@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcNotificationRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Notification insert(Notification n) {
        NotificationRow row = jdbc.sql("""
                INSERT INTO admin.notification (
                    business_id, tenant_schema, recipient_user_id, recipient_scope,
                    type, title, body, ref_type, ref_id, payload)
                VALUES (
                    :businessId, :tenantSchema, :recipientUserId, :recipientScope,
                    :type, :title, :body, :refType, :refId, CAST(:payload AS jsonb))
                RETURNING id, public_id, business_id, tenant_schema, recipient_user_id, recipient_scope,
                          type, title, body, ref_type, ref_id, payload::text AS payload, read_at, created_at
                """)
                .param("businessId", n.businessId())
                .param("tenantSchema", n.tenantSchema())
                .param("recipientUserId", n.recipientUserId())
                .param("recipientScope", n.recipientScope() == null ? null : n.recipientScope().name())
                .param("type", n.type())
                .param("title", n.title())
                .param("body", n.body())
                .param("refType", n.refType())
                .param("refId", n.refId())
                .param("payload", toJson(n.payload()))
                .query(NotificationRow.class)
                .single();
        return toNotification(row);
    }

    @Override
    public List<Notification> findInbox(NotificationScope scope, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, public_id, business_id, tenant_schema, recipient_user_id, recipient_scope,
                       type, title, body, ref_type, ref_id, payload::text AS payload, read_at, created_at
                FROM admin.notification
                WHERE
                """);
        sql.append(scopeWhere(scope));
        sql.append(" ORDER BY (read_at IS NULL) DESC, created_at DESC, id DESC LIMIT :limit");

        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        spec = bindScope(spec, scope);
        spec = spec.param("limit", limit);
        return spec.query(NotificationRow.class).list().stream().map(this::toNotification).toList();
    }

    @Override
    public long countUnread(NotificationScope scope) {
        String sql = "SELECT count(*) FROM admin.notification WHERE " + scopeWhere(scope)
                + " AND read_at IS NULL";
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        spec = bindScope(spec, scope);
        return spec.query(Long.class).single();
    }

    @Override
    public int markRead(UUID publicId, NotificationScope scope) {
        // UN solo roundtrip (M4/H-A4): UPDATE idempotente + RETURNING, sin SELECT
        // previo.
        String sql = """
                UPDATE admin.notification
                SET read_at = COALESCE(read_at, now())
                WHERE public_id = :publicId AND """ + scopeWhere(scope) + " RETURNING id";
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        spec = bindScope(spec, scope);
        spec = spec.param("publicId", publicId);
        return spec.query(Long.class).optional().isPresent() ? 1 : 0;
    }

    @Override
    public int markAllRead(NotificationScope scope) {
        String sql = "UPDATE admin.notification SET read_at = now() WHERE " + scopeWhere(scope)
                + " AND read_at IS NULL";
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        spec = bindScope(spec, scope);
        return spec.update();
    }

    @Override
    public int markReadBySupportTicketForSuperAdmin(String ticketPublicId) {
        return jdbc.sql("""
                UPDATE admin.notification SET read_at = now()
                WHERE recipient_scope = 'SUPER_ADMIN'
                  AND ref_type = 'support_ticket'
                  AND ref_id = :ticketPublicId
                  AND read_at IS NULL
                """)
                .param("ticketPublicId", ticketPublicId)
                .update();
    }

    // ----- Helpers de scope (WHERE determinista por alcance, N2) -----

    private static String scopeWhere(NotificationScope scope) {
        return switch (scope) {
            case NotificationScope.User ignored ->
                "(business_id = :bid AND recipient_user_id = :uid)";
            case NotificationScope.SuperAdmin ignored -> "(recipient_scope = 'SUPER_ADMIN')";
        };
    }

    private static JdbcClient.StatementSpec bindScope(JdbcClient.StatementSpec spec,
            NotificationScope scope) {
        if (scope instanceof NotificationScope.User user) {
            return spec.param("bid", user.businessId()).param("uid", user.userId());
        }
        return spec;
    }

    // ----- Mapping -----

    private Notification toNotification(NotificationRow r) {
        return new Notification(
                r.id(),
                r.publicId(),
                r.businessId(),
                r.tenantSchema(),
                r.recipientUserId(),
                r.recipientScope() == null ? null : RecipientScope.valueOf(r.recipientScope()),
                r.type(),
                r.title(),
                r.body(),
                r.refType(),
                r.refId(),
                fromJson(r.payload()),
                toInstant(r.readAt()),
                toInstant(r.createdAt()));
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(payload);
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, MAP_TYPE);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    /** Proyección de fila de {@code admin.notification}. */
    public record NotificationRow(
            Long id,
            UUID publicId,
            Long businessId,
            String tenantSchema,
            String recipientUserId,
            String recipientScope,
            String type,
            String title,
            String body,
            String refType,
            String refId,
            String payload,
            Timestamp readAt,
            Timestamp createdAt) {
    }
}
