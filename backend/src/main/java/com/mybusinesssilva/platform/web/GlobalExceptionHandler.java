package com.mybusinesssilva.platform.web;

import com.mybusinesssilva.catalog.domain.model.ProductHasStockException;
import com.mybusinesssilva.inventory.domain.model.InsufficientStockException;
import com.mybusinesssilva.sales.domain.model.ShiftReopenException;
import com.mybusinesssilva.support.application.SupportTicketService.SupportImageRejectedException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Manejo global de errores para la API. Traduce las excepciones de negocio y de
 * validación a
 * respuestas HTTP claras y consistentes, sin filtrar detalles internos (stack
 * traces).
 *
 * <p>
 * Usa {@link ProblemDetail} (RFC 7807), el estándar de Spring 6+/Boot 3+ para
 * errores.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Errores de reglas de negocio (por ejemplo, límite de crédito excedido, plan
     * inexistente).
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
     * Estados inválidos de una operación (por ejemplo, cancelar un CFDI no
     * timbrado).
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
     * Errores de validación de los datos de entrada (Bean Validation). Devuelve 400
     * con el
     * detalle de los campos inválidos.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("INSUFFICIENT_STOCK");
        problem.setProperty("productId", ex.productId());
        problem.setProperty("productName", ex.productName());
        problem.setProperty("available", ex.available());
        problem.setProperty("requested", ex.requested());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Barrera dura: no se puede desactivar un producto con existencia normal &gt; 0.
     * Espejo de {@link #handleInsufficientStock}: 409 CONFLICT con el detalle del
     * producto y su stock. No se loguea (es una regla de negocio esperada).
     */
    @ExceptionHandler(ProductHasStockException.class)
    public ProblemDetail handleProductHasStock(ProductHasStockException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("PRODUCT_HAS_STOCK");
        problem.setProperty("productId", ex.productId());
        problem.setProperty("productName", ex.productName());
        problem.setProperty("stock", ex.stock());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Reapertura de turno inválida (OWNER-only): turno inexistente, no cerrado, de
     * un día pasado, caja ocupada, o desajuste de modo prueba. Espejo de
     * {@link #handleProductHasStock}: 409 CONFLICT con {@code title = code} estable
     * y las propiedades del caso para que el front pinte un modal claro. No se
     * loguea (son reglas de negocio esperadas).
     */
    @ExceptionHandler(ShiftReopenException.class)
    public ProblemDetail handleShiftReopen(ShiftReopenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle(ex.code());
        ex.props().forEach(problem::setProperty);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Rechazo de alta de ticket por imagen inválida (Rama A del buzón de soporte, §11.8). Espejo del
     * 422 de negocio, con {@code title = IMAGE_INVALID} y la propiedad {@code invalidImages} con el
     * índice y la razón ({@code SIZE|TYPE|COUNT}) de cada imagen descartada. Se declara ANTES del
     * handler de {@link IllegalArgumentException} (del que hereda) para que gane por especificidad.
     */
    @ExceptionHandler(SupportImageRejectedException.class)
    public ProblemDetail handleSupportImageRejected(SupportImageRejectedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("IMAGE_INVALID");
        problem.setProperty("invalidImages", ex.invalidImages().stream()
                .map(r -> Map.of("index", r.index(), "reason", r.reason()))
                .toList());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Imagen que supera el tamaño permitido por el contenedor multipart ({@code
     * spring.servlet.multipart.max-file-size}). Sin este handler caería al fallback 500 opaco; aquí
     * se traduce a un 422 legible con {@code title = IMAGE_TOO_LARGE}.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Alguna imagen supera el tamaño permitido (máx. 5 MB por imagen).");
        problem.setTitle("IMAGE_TOO_LARGE");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

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

    /**
     * Errores de acceso a datos de Spring JDBC (SQL mal formado, violación de
     * restricción, o
     * conexión perdida con la base — p. ej. Neon cerrando conexiones ociosas). Se
     * loguea el
     * {@code most specific cause} para que el error deje de ser opaco en los logs y
     * se devuelve
     * un 500 con un mensaje genérico (sin filtrar la consulta ni el detalle interno
     * al cliente).
     */
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccess(DataAccessException ex) {
        log.error("Data access error on request: {}", ex.getMostSpecificCause().getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un problema al consultar la información. Inténtalo de nuevo en un momento.");
        problem.setTitle("Error de datos");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Fallback genérico para cualquier excepción no mapeada arriba. Antes de este
     * handler, una
     * excepción inesperada (NPE, error de runtime, etc.) se propagaba sin traducir
     * y
     * terminaba
     * en un 500 OPACO con el stack del dispatcher de Spring. Ahora el root cause
     * completo queda
     * en los logs y el cliente recibe un ProblemDetail 500 limpio, sin detalles
     * internos.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception on request", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Inténtalo de nuevo; si continúa, avisa a soporte.");
        problem.setTitle("Error interno");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
