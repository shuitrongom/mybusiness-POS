package com.mybusinesssilva.notifications.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Notificación genérica del centro de notificaciones (schema {@code admin}). Modelo de dominio puro.
 *
 * @param id              identificador interno (nullable antes de persistir)
 * @param publicId        identificador externo UUID (nullable antes de persistir)
 * @param businessId      negocio destino (requerido si scope=USER; null si scope=SUPER_ADMIN)
 * @param tenantSchema    schema de origen (nullable)
 * @param recipientUserId usuario destino (requerido si scope=USER; null si scope=SUPER_ADMIN)
 * @param recipientScope  USER | SUPER_ADMIN
 * @param type            tipo de la notificación (ver {@link NotificationType})
 * @param title           título (máx. 200 code points)
 * @param body            cuerpo (máx. 600 code points, nullable)
 * @param refType         tipo de la entidad referenciada (nullable)
 * @param refId           identificador de la entidad referenciada (nullable)
 * @param payload         datos adicionales serializados a JSONB (nullable)
 * @param readAt          marca de lectura (null si no leída)
 * @param createdAt       alta (nullable antes de persistir)
 */
public record Notification(
        Long id,
        UUID publicId,
        Long businessId,
        String tenantSchema,
        String recipientUserId,
        RecipientScope recipientScope,
        String type,
        String title,
        String body,
        String refType,
        String refId,
        Map<String, Object> payload,
        Instant readAt,
        Instant createdAt) {
}
