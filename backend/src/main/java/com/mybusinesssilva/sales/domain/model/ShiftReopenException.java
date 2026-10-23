package com.mybusinesssilva.sales.domain.model;

import java.util.Map;

/**
 * Barrera de dominio para la REAPERTURA DE TURNO (OWNER-only): una reapertura
 * inválida (turno inexistente, no cerrado, de un día pasado, caja ocupada, o con
 * desajuste de modo prueba) se señala con un {@code code} estable y propiedades
 * útiles para que el frontend pinte un modal premium según el caso.
 *
 * <p>
 * Un solo tipo tipado (con {@code code} + {@code props}) para no multiplicar
 * clases. Se mapea a un 409 CONFLICT con {@code title = code} en
 * {@link com.mybusinesssilva.platform.web.GlobalExceptionHandler}, espejo de
 * {@code ProductHasStockException}. Sin dependencias de Spring.
 *
 * <p>
 * Códigos esperados: {@code SHIFT_NOT_FOUND}, {@code SHIFT_NOT_CLOSED},
 * {@code SHIFT_REOPEN_WINDOW_EXPIRED}, {@code SHIFT_REGISTER_BUSY},
 * {@code SHIFT_REOPEN_TEST_MODE_MISMATCH}.
 */
public class ShiftReopenException extends RuntimeException {

    private final String code;
    private final transient Map<String, Object> props;

    public ShiftReopenException(String code, String message, Map<String, Object> props) {
        super(message);
        this.code = code;
        this.props = props == null ? Map.of() : props;
    }

    /** @return el código estable de la regla de negocio (sirve de {@code title}). */
    public String code() {
        return code;
    }

    /** @return propiedades extra para el {@code ProblemDetail} (shiftId, status, ...). */
    public Map<String, Object> props() {
        return props;
    }
}
