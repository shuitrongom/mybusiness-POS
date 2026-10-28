package com.mybusinesssilva.notifications.domain.port.in;

/**
 * Alcance de consulta/escritura del centro de notificaciones. Se deriva siempre server-side a partir
 * del usuario autenticado (nunca del cliente).
 */
public sealed interface NotificationScope {

    /** Usuario de un negocio: solo ve sus propias notificaciones. */
    record User(long businessId, String userId) implements NotificationScope {
    }

    /** Super-admin: ve la bandeja global del equipo PuntoNube. */
    record SuperAdmin() implements NotificationScope {
    }
}
