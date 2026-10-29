package com.mybusinesssilva.support.adapters.out.persistence;

import com.mybusinesssilva.support.domain.port.out.SupportInboundEventRepository;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC de {@link SupportInboundEventRepository} sobre {@code admin.support_inbound_event}
 * (Fase 2). Mismo patrón {@link JdbcClient} que {@link JdbcSupportMessageRepository}. El claim usa
 * {@code INSERT … ON CONFLICT (event_id) DO NOTHING} (guardia idempotente), como el scheduler de
 * panadería.
 */
@Repository
public class JdbcSupportInboundEventRepository implements SupportInboundEventRepository {

    private final JdbcClient jdbc;

    public JdbcSupportInboundEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryClaim(String eventId) {
        int rows = jdbc.sql("""
                INSERT INTO admin.support_inbound_event (event_id, outcome, reason)
                VALUES (:id, 'PROCESSING', 'claiming')
                ON CONFLICT (event_id) DO NOTHING
                """)
                .param("id", eventId)
                .update();
        return rows == 1;
    }

    @Override
    public void finalizeEvent(String eventId, String outcome, String reason,
                              Long ticketId, Long messageId) {
        jdbc.sql("""
                UPDATE admin.support_inbound_event
                   SET outcome = :outcome, reason = :reason, ticket_id = :ticketId,
                       message_id = :messageId, updated_at = now()
                 WHERE event_id = :id
                """)
                .param("id", eventId)
                .param("outcome", outcome)
                .param("reason", reason)
                .param("ticketId", ticketId)
                .param("messageId", messageId)
                .update();
    }

    @Override
    public Optional<String> outcomeOf(String eventId) {
        return jdbc.sql("SELECT outcome FROM admin.support_inbound_event WHERE event_id = :id")
                .param("id", eventId)
                .query(String.class)
                .optional();
    }
}
