package com.mybusinesssilva.support.adapters.out.persistence;

import com.mybusinesssilva.support.domain.model.AuthorType;
import com.mybusinesssilva.support.domain.model.SupportTicketMessage;
import com.mybusinesssilva.support.domain.port.out.SupportMessageRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC de {@link SupportMessageRepository} sobre el schema {@code admin}. Mismo patrón
 * que {@link JdbcSupportTicketRepository}: {@link JdbcClient} con parámetros nombrados.
 */
@Repository
public class JdbcSupportMessageRepository implements SupportMessageRepository {

    private final JdbcClient jdbc;

    public JdbcSupportMessageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SupportTicketMessage> findByTicketId(long ticketId) {
        List<MessageRow> rows = jdbc.sql("""
                SELECT id, public_id, ticket_id, author_type, author_role, author_user_id,
                       author_email, body, app_version, screen_path, created_at, source
                FROM admin.support_ticket_message
                WHERE ticket_id = :ticketId
                ORDER BY created_at ASC, id ASC
                """)
                .param("ticketId", ticketId)
                .query(MessageRow.class)
                .list();
        return rows.stream().map(JdbcSupportMessageRepository::toMessage).toList();
    }

    @Override
    public SupportTicketMessage insert(SupportTicketMessage message) {
        MessageRow row = jdbc.sql("""
                INSERT INTO admin.support_ticket_message (
                    ticket_id, author_type, author_role, author_user_id, author_email,
                    body, app_version, screen_path)
                VALUES (
                    :ticketId, :authorType, :authorRole, :authorUserId, :authorEmail,
                    :body, :appVersion, :screenPath)
                RETURNING id, public_id, ticket_id, author_type, author_role, author_user_id,
                          author_email, body, app_version, screen_path, created_at, source
                """)
                .param("ticketId", message.ticketId())
                .param("authorType", message.authorType() == null ? null : message.authorType().name())
                .param("authorRole", message.authorRole())
                .param("authorUserId", message.authorUserId())
                .param("authorEmail", message.authorEmail())
                .param("body", message.body())
                .param("appVersion", message.appVersion())
                .param("screenPath", message.screenPath())
                .query(MessageRow.class)
                .single();
        return toMessage(row);
    }

    @Override
    public void markSource(long messageId, String source) {
        jdbc.sql("UPDATE admin.support_ticket_message SET source = :source WHERE id = :id")
                .param("source", source)
                .param("id", messageId)
                .update();
    }

    @Override
    public Optional<Instant> lastSuperAdminMessageAt(long ticketId, Instant before) {
        Optional<Timestamp> ts = jdbc.sql("""
                SELECT max(created_at) FROM admin.support_ticket_message
                WHERE ticket_id = :ticketId
                  AND author_type = 'SUPER_ADMIN'
                  AND created_at < :before
                """)
                .param("ticketId", ticketId)
                .param("before", before == null ? null : Timestamp.from(before))
                .query(Timestamp.class)
                .optional();
        return ts.map(Timestamp::toInstant);
    }

    private static SupportTicketMessage toMessage(MessageRow r) {
        return new SupportTicketMessage(
                r.id(),
                r.publicId(),
                r.ticketId(),
                r.authorType() == null ? null : AuthorType.valueOf(r.authorType()),
                r.authorRole(),
                r.authorUserId(),
                r.authorEmail(),
                r.body(),
                r.appVersion(),
                r.screenPath(),
                r.createdAt() == null ? null : r.createdAt().toInstant(),
                r.source());
    }

    /** Proyección de fila de {@code admin.support_ticket_message}. */
    public record MessageRow(
            Long id,
            UUID publicId,
            long ticketId,
            String authorType,
            String authorRole,
            String authorUserId,
            String authorEmail,
            String body,
            String appVersion,
            String screenPath,
            Timestamp createdAt,
            String source) {
    }
}
