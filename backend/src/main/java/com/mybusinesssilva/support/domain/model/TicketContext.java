package com.mybusinesssilva.support.domain.model;

/**
 * Contexto automático capturado al crear un ticket. Lo resuelve el controller a partir del
 * {@code AuthenticatedUser}, el tenant y el request (nunca se confía en el cliente).
 *
 * @param tenantSchema    schema de origen (tenant_9, etc.)
 * @param businessId      negocio resuelto desde {@code admin.business.schema_name}
 * @param reporterUserId  subject del token (correo/id)
 * @param reporterEmail   correo del usuario que reporta (nullable)
 * @param reporterRole    rol del usuario (OWNER | CASHIER | ADMIN | ...)
 * @param screenPath      ruta del front desde la que se reportó (nullable)
 * @param appVersion      versión del front enviada por el cliente (nullable)
 * @param userAgent       User-Agent del request, ya truncado a 500 (nullable)
 * @param device          dispositivo clasificado (mobile | desktop | unknown)
 */
public record TicketContext(
        String tenantSchema,
        Long businessId,
        String reporterUserId,
        String reporterEmail,
        String reporterRole,
        String screenPath,
        String appVersion,
        String userAgent,
        String device) {
}
