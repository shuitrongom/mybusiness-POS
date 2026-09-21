package com.mybusinesssilva.platform.web;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejo global de errores para la API. Traduce las excepciones de negocio y de validación a
 * respuestas HTTP claras y consistentes, sin filtrar detalles internos (stack traces).
 *
 * <p>Usa {@link ProblemDetail} (RFC 7807), el estándar de Spring 6+/Boot 3+ para errores.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Errores de reglas de negocio (por ejemplo, límite de crédito excedido, plan inexistente).
     * Se traducen a 422 (Unprocessable Entity) con el mensaje de negocio.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBusinessError(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Regla de negocio");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Estados inválidos de una operación (por ejemplo, cancelar un CFDI no timbrado).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleInvalidState(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Estado inválido");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Errores de validación de los datos de entrada (Bean Validation). Devuelve 400 con el
     * detalle de los campos inválidos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(
                error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Datos de entrada inválidos");
        problem.setTitle("Validación");
        problem.setProperty("errors", fieldErrors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
