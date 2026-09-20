package com.mybusinesssilva.platform.security;

/**
 * Roles predefinidos del sistema. Cada negocio puede además crear roles personalizados.
 */
public final class Roles {

    /** Proveedor del SaaS: crea negocios, controla licencias y módulos. */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** Dueño del negocio: control total dentro de su empresa. */
    public static final String OWNER = "OWNER";

    /** Administrador del negocio. */
    public static final String ADMIN = "ADMIN";

    /** Supervisor: autoriza operaciones sensibles (cancelaciones, etc.). */
    public static final String SUPERVISOR = "SUPERVISOR";

    /** Cajero: opera el punto de venta. */
    public static final String CASHIER = "CASHIER";

    private Roles() {
        // Constantes: no instanciable.
    }
}
