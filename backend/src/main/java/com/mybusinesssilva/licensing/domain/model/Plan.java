package com.mybusinesssilva.licensing.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Plan comercial: un paquete de módulos con un precio de licencia definitiva sugerido.
 *
 * @param id                   identificador (nulo hasta persistir)
 * @param code                 código único del plan (ESSENTIAL, PROFESSIONAL, ENTERPRISE, ...)
 * @param name                 nombre visible
 * @param description          descripción
 * @param licensePriceSuggested precio de licencia sugerido (pago único)
 * @param active               si el plan está disponible para venta
 * @param moduleKeys           claves de módulos incluidos
 */
public record Plan(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal licensePriceSuggested,
        boolean active,
        List<String> moduleKeys) {

    public Plan {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código del plan es obligatorio");
        }
        moduleKeys = moduleKeys == null ? List.of() : List.copyOf(moduleKeys);
    }
}
