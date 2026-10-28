package com.mybusinesssilva.notifications.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Se lanza cuando una notificación no existe o no es visible para el solicitante. Anotada con
 * {@link ResponseStatus} {@code NOT_FOUND} para devolver 404 sin filtrar notificaciones ajenas.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(String message) {
        super(message);
    }
}
