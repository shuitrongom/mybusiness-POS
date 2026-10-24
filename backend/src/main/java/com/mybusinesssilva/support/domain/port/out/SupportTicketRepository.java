package com.mybusinesssilva.support.domain.port.out;

import com.mybusinesssilva.support.domain.model.SupportTicket;
import com.mybusinesssilva.support.domain.model.SupportTicketImage;
import com.mybusinesssilva.support.domain.model.TicketStatus;
import com.mybusinesssilva.support.domain.port.in.RequesterScope;
import com.mybusinesssilva.support.domain.port.in.TicketQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida de persistencia del buzón de soporte (schema {@code admin}).
 */
public interface SupportTicketRepository {

    /** Inserta el ticket y lo devuelve con {@code id}/{@code public_id} resueltos. */
    SupportTicket insert(SupportTicket ticket);

    /** Inserta las imágenes del ticket (FK {@code ticket_id} = id interno tras el insert). */
    void insertImages(long ticketId, List<SupportTicketImage> images);

    /** Busca un ticket por su identificador externo (UUID), con sus imágenes. */
    Optional<SupportTicket> findByPublicId(UUID publicId);

    /** Lista tickets aplicando el alcance ({@code scope}) y los filtros ({@code query}). */
    List<SupportTicket> find(TicketQuery query, RequesterScope scope);

    /** Actualiza estado/nota/resolución de un ticket por {@code publicId} y lo devuelve. */
    SupportTicket updateStatusByPublicId(UUID publicId, TicketStatus status, String note,
                                         String resolvedBy, Instant resolvedAt);

    /** Resuelve el {@code business_id} desde {@code admin.business.schema_name}. */
    Optional<Long> resolveBusinessIdBySchema(String tenantSchema);
}
