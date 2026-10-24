package com.mybusinesssilva.support.domain.model;

/**
 * Tipo de ticket de soporte/feedback.
 *
 * <ul>
 *   <li>{@link #ERROR}: reporte de un fallo del sistema.</li>
 *   <li>{@link #MEJORA}: petición de mejora o nueva funcionalidad.</li>
 * </ul>
 */
public enum TicketType {
    ERROR,
    MEJORA
}
