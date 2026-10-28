package com.mybusinesssilva.support.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje del hilo de conversación de un ticket de soporte. Modelo de dominio puro.
 *
 * <p>{@code authorRole} es nullable (traza fina, N-R5-3). {@code appVersion}/{@code screenPath} son
 * nullable (contexto del cliente del reportante, N1/D20). Ninguno participa en autorización ni render.
 *
 * @param id           identificador interno (nullable antes de persistir)
 * @param publicId     identificador externo UUID (nullable antes de persistir)
 * @param ticketId     ticket al que pertenece el mensaje
 * @param authorType   SUPER_ADMIN | REPORTER
 * @param authorRole   rol fino del autor (OWNER|ADMIN|SUPERVISOR|CASHIER|SUPER_ADMIN), nullable
 * @param authorUserId subject del token del autor
 * @param authorEmail  correo del autor (nullable)
 * @param body         cuerpo del mensaje (saneado, máx. 4000 code points)
 * @param appVersion   versión del front del autor (nullable, traza interna)
 * @param screenPath   ruta del front del autor (nullable, traza interna)
 * @param createdAt    alta (nullable antes de persistir)
 */
public record SupportTicketMessage(
        Long id,
        UUID publicId,
        long ticketId,
        AuthorType authorType,
        String authorRole,
        String authorUserId,
        String authorEmail,
        String body,
        String appVersion,
        String screenPath,
        Instant createdAt) {
}
