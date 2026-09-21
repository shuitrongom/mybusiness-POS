package com.mybusinesssilva.licensing.domain.model;

/**
 * Tipo de conexión de datos de un negocio.
 */
public enum ConnectionKind {

    /** Comparte el servidor PostgreSQL con otros negocios (schema propio). Modelo por defecto. */
    DEFAULT,

    /** Base de datos dedicada para el negocio (clientes que lo requieran/paguen). */
    DEDICATED
}
