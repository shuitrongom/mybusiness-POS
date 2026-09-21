package com.mybusinesssilva.licensing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Módulo habilitado comercialmente para un negocio.
 *
 * @param moduleKey clave del módulo
 * @param enabled   si está habilitado actualmente
 * @param origin    origen de la habilitación (parte del plan o excedente vendido)
 * @param soldPrice precio con el que se vendió (nulo si vino incluido en el plan)
 * @param soldAt    fecha de la venta/habilitación
 */
public record BusinessModule(
        String moduleKey,
        boolean enabled,
        ModuleOrigin origin,
        BigDecimal soldPrice,
        Instant soldAt) {

    /** Origen de la habilitación de un módulo. */
    public enum ModuleOrigin {
        /** Incluido en el plan contratado. */
        PLAN,
        /** Vendido después como módulo adicional (excedente, pago único). */
        SURCHARGE
    }
}
