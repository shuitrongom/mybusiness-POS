package com.mybusinesssilva.support.adapters.in.rest.dto;

import com.mybusinesssilva.support.domain.model.TicketType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload JSON (parte {@code data} del multipart) para crear un ticket. La barrera dura de validación
 * vive en el servicio (§5.4); estos constraints son la primera línea (Bean Validation → 400).
 *
 * @param type        tipo del ticket (ERROR | MEJORA)
 * @param title       título (3–160)
 * @param description descripción (5–4000)
 * @param screenPath  ruta del front (opcional)
 * @param appVersion  versión del front (opcional)
 */
public record CreateTicketRequest(
        @NotNull TicketType type,
        @Size(min = 3, max = 160) String title,
        @Size(min = 5, max = 4000) String description,
        @Size(max = 300) String screenPath,
        @Size(max = 60) String appVersion) {
}
