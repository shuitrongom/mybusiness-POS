package com.mybusinesssilva.support.domain.port.in;

/**
 * Alcance de visibilidad del solicitante. Se construye en el controller a partir del
 * {@code AuthenticatedUser} (nunca del cliente) y el servicio lo traduce a las cláusulas
 * {@code WHERE} del repositorio, imponiendo la barrera de aislamiento por negocio.
 */
public sealed interface RequesterScope {

    /** Super-admin: ve todos los tickets de todos los negocios. */
    record SuperAdmin() implements RequesterScope {
    }

    /** Dueño/administrador: ve todos los tickets de su negocio. */
    record BusinessOwner(long businessId) implements RequesterScope {
    }

    /** Reportero (cajera u otro rol de tenant): solo ve los tickets que él mismo creó. */
    record Reporter(long businessId, String reporterUserId) implements RequesterScope {
    }
}
