package com.mybusinesssilva.support.adapters.in.rest.dto;

import com.mybusinesssilva.support.domain.model.AuthorType;
import com.mybusinesssilva.support.domain.model.SupportTicketMessage;
import java.util.UUID;

/**
 * DTO de respuesta REST de un mensaje del hilo de soporte.
 *
 * <p>Oculta {@code authorEmail} cuando el autor es el super-admin (ocultación server-side N-R4-4).
 * No expone {@code appVersion}/{@code screenPath} (traza interna, D20) ni {@code authorRole} (N-R5-3).
 */
public record MessageView(
        UUID publicId,
        String authorType,
        String authorEmail,
        String body,
        String createdAt) {

    /** Mapper desde dominio. Nullifica authorEmail si authorType == SUPER_ADMIN. */
    public static MessageView from(SupportTicketMessage msg) {
        return new MessageView(
                msg.publicId(),
                msg.authorType().name(),
                msg.authorType() == AuthorType.SUPER_ADMIN ? null : msg.authorEmail(),
                msg.body(),
                msg.createdAt() == null ? null : msg.createdAt().toString());
    }
}
