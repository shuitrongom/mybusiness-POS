package com.mybusinesssilva.support.domain.port.in;

/**
 * Identidad de quien ejecuta una operación sobre un ticket (para auditoría y campos
 * {@code resolved_by}). Se deriva del {@code AuthenticatedUser} en el controller.
 *
 * @param subject   identificador del actor (correo/id del token)
 * @param device    dispositivo (mobile | desktop | unknown)
 * @param userAgent User-Agent truncado a 500 (nullable)
 */
public record Actor(String subject, String device, String userAgent) {
}
