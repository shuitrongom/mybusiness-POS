package com.mybusinesssilva.invoicing.domain.model;

/**
 * Estado de un CFDI.
 */
public enum CfdiStatus {
    /** Creado, aún no timbrado. */
    PENDING,
    /** Timbrado con éxito ante el SAT (tiene UUID). */
    STAMPED,
    /** Cancelado. */
    CANCELED,
    /** Error de timbrado (permite reintentar). */
    ERROR
}
