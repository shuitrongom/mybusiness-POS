package com.mybusinesssilva.platform.tenancy;

/**
 * Guarda el identificador del tenant (empresa) de la petición en curso.
 *
 * <p>Se resuelve una sola vez por petición (a partir del subdominio, un encabezado o un
 * claim del JWT) y queda disponible durante toda la ejecución de esa petición. El resto
 * del sistema no accede a este contexto directamente; lo consume la capa de persistencia
 * para seleccionar el schema del tenant y activar la política de Row-Level Security.
 *
 * <p>Se implementa con {@link ThreadLocal}, por lo que cada hilo de petición tiene su propio
 * valor aislado. Es indispensable limpiarlo al terminar la petición (ver {@code TenantFilter})
 * para evitar fugas de contexto entre peticiones que reutilizan el mismo hilo.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Fija el tenant de la petición en curso.
     *
     * @param tenantId identificador del schema del tenant (por ejemplo, {@code tenant_12})
     */
    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * @return el tenant de la petición en curso, o {@code null} si no se ha fijado
     *         (por ejemplo, en operaciones globales del schema {@code admin}).
     */
    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    /**
     * @return {@code true} si hay un tenant fijado en la petición en curso.
     */
    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }

    /**
     * Limpia el tenant de la petición en curso. Debe llamarse siempre al terminar la
     * petición para no filtrar el valor a peticiones posteriores del mismo hilo.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
