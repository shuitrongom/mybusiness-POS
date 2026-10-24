package com.mybusinesssilva.support.adapters.in.rest.dto;

import com.mybusinesssilva.support.domain.model.SupportTicket;
import com.mybusinesssilva.support.domain.model.TicketStatus;
import com.mybusinesssilva.support.domain.model.TicketType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vista de detalle de un ticket (identificador externo {@code publicId}; nunca el {@code id}
 * secuencial interno). Incluye contexto y galería de imágenes.
 */
public record TicketView(
        UUID publicId,
        Long businessId,
        String tenantSchema,
        String reporterUserId,
        String reporterEmail,
        String reporterRole,
        TicketType type,
        String title,
        String description,
        String screenPath,
        String appVersion,
        String userAgent,
        String device,
        TicketStatus status,
        String resolutionNote,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        String resolvedBy,
        List<TicketImageView> images) {

    public static TicketView from(SupportTicket t) {
        return new TicketView(
                t.publicId(), t.businessId(), t.tenantSchema(),
                t.reporterUserId(), t.reporterEmail(), t.reporterRole(),
                t.type(), t.title(), t.description(), t.screenPath(), t.appVersion(),
                t.userAgent(), t.device(), t.status(), t.resolutionNote(),
                t.createdAt(), t.updatedAt(), t.resolvedAt(), t.resolvedBy(),
                t.images() == null ? List.of()
                        : t.images().stream().map(TicketImageView::from).toList());
    }
}
