package com.mybusinesssilva.support.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ticket de soporte / feedback. Modelo de dominio puro (sin anotaciones de Spring).
 *
 * <p>El identificador externo es {@link #publicId} (UUID); el {@link #id} secuencial interno solo se
 * usa para la FK de las imágenes tras el insert y nunca se expone.
 *
 * @param id              identificador interno (nullable antes de persistir)
 * @param publicId        identificador externo UUID (nullable antes de persistir)
 * @param businessId      negocio dueño del ticket (nullable solo como red del esquema)
 * @param tenantSchema    schema de origen (tenant_9, etc.)
 * @param reporterUserId  subject del token (correo/id)
 * @param reporterEmail   correo del usuario que reporta (nullable)
 * @param reporterRole    rol del usuario que reporta
 * @param type            tipo del ticket (ERROR | MEJORA)
 * @param title           título (3–160 chars)
 * @param description     descripción (5–4000 chars)
 * @param screenPath      ruta del front (nullable)
 * @param appVersion      versión del front (nullable)
 * @param userAgent       User-Agent truncado a 500 (nullable)
 * @param device          dispositivo (mobile | desktop | unknown)
 * @param status          estado del ciclo de vida
 * @param resolutionNote  nota opcional al resolver/descartar (nullable)
 * @param createdAt       alta (nullable antes de persistir)
 * @param updatedAt       última actualización (nullable antes de persistir)
 * @param resolvedAt      fecha de resolución/descarte (nullable)
 * @param resolvedBy      quién resolvió/descartó (nullable)
 * @param images          imágenes adjuntas
 */
public record SupportTicket(
        Long id,
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
        List<SupportTicketImage> images) {
}
