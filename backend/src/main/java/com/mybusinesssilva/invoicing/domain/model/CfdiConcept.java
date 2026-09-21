package com.mybusinesssilva.invoicing.domain.model;

import java.math.BigDecimal;

/**
 * Concepto (renglón) de un CFDI.
 *
 * @param satProdServ clave SAT de producto/servicio (obligatoria en CFDI 4.0)
 * @param satUnit     clave SAT de unidad
 * @param description descripción
 * @param quantity    cantidad
 * @param unitPrice   valor unitario
 * @param amount      importe (cantidad * valor unitario)
 */
public record CfdiConcept(
        String satProdServ,
        String satUnit,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount) {

    public CfdiConcept {
        if (satProdServ == null || satProdServ.isBlank()) {
            throw new IllegalArgumentException("El concepto requiere clave SAT de producto/servicio");
        }
    }
}
