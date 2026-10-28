package com.mybusinesssilva.support.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body del endpoint POST del hilo de mensajes. El {@code author_type} nunca viene del cliente: lo
 * fija el controller según el endpoint (REPORTER o SUPER_ADMIN).
 *
 * @param body       cuerpo del mensaje (obligatorio; se sanea y trunca server-side)
 * @param screenPath ruta del front (opcional; solo reportante)
 * @param appVersion versión del front (opcional)
 */
public record PostMessageRequest(
        @NotBlank String body,
        String screenPath,
        String appVersion) {
}
