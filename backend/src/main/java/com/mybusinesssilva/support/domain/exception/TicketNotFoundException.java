package com.mybusinesssilva.support.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Se lanza cuando un ticket no existe o no es visible para el solicitante. Anotada con
 * {@link ResponseStatus} {@code NOT_FOUND} para devolver 404 sin filtrar la existencia de tickets
 * ajenos (ver §5.5 del diseño).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(String message) {
        super(message);
    }
}
