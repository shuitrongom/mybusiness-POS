package com.mybusinesssilva.support.adapters.in.rest.dto;

import com.mybusinesssilva.support.domain.model.SupportTicket;
import com.mybusinesssilva.support.domain.model.TicketStatus;
import com.mybusinesssilva.support.domain.model.TicketType;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila ligera para las listas/bandejas (sin descripción larga ni galería completa; solo el conteo de
 * imágenes), alineada con las filas-tarjeta del steering premium.
 */
public record TicketListItem(
        UUID publicId,
        Long businessId,
        TicketType type,
        String title,
        TicketStatus status,
        String reporterEmail,
        String reporterRole,
        String device,
        int imageCount,
        Instant createdAt,
        Instant updatedAt) {

    public static TicketListItem from(SupportTicket t) {
        return new TicketListItem(
                t.publicId(), t.businessId(), t.type(), t.title(), t.status(),
                t.reporterEmail(), t.reporterRole(), t.device(),
                t.images() == null ? 0 : t.images().size(),
                t.createdAt(), t.updatedAt());
    }
}
