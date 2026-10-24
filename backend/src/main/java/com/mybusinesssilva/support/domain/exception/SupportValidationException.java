package com.mybusinesssilva.support.domain.exception;

/**
 * Error de validación de negocio del buzón de soporte. Extiende {@link IllegalArgumentException}
 * para que el {@code GlobalExceptionHandler} existente la mapee a HTTP 422.
 */
public class SupportValidationException extends IllegalArgumentException {

    public SupportValidationException(String message) {
        super(message);
    }
}
