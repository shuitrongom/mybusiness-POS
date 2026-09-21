package com.mybusinesssilva.licensing.domain.model;

/**
 * Estado del ciclo de licencia de un negocio.
 */
public enum BusinessStatus {

    /** En periodo de prueba. Acceso completo hasta la fecha de vencimiento. */
    TRIAL,

    /** Licencia definitiva comprada. Acceso permanente a los módulos adquiridos. */
    ACTIVE,

    /** Suspendido por el Super Admin. Sin acceso, pero se conservan los datos. */
    SUSPENDED,

    /** Prueba vencida sin compra. Sin acceso hasta adquirir la licencia. */
    EXPIRED;

    /** @return true si el estado permite el acceso operativo al sistema. */
    public boolean allowsAccess() {
        return this == TRIAL || this == ACTIVE;
    }
}
