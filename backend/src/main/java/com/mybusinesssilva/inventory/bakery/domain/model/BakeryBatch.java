package com.mybusinesssilva.inventory.bakery.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Tanda (hornada): documento que liga el consumo de materia prima con la
 * producción de pan en una sola transacción atómica.
 *
 * @param id           identificador (null antes de persistir)
 * @param folio        folio ('T-'||id), asignado tras el insert
 * @param branchId     sucursal
 * @param batchDate    fecha natural MX de la tanda (explícita, consistente con la producción)
 * @param productionId producción de pan ligada (null solo durante el tx; siempre poblada al confirmar)
 * @param inputs       líneas de insumo consumido
 * @param outputs      líneas de pan producido
 * @param notes        notas
 * @param createdBy    actor (el panadero: actor.subject())
 * @param createdRole  rol operativo del actor ('PANADERO', OWNER, ADMIN, …)
 */
public record BakeryBatch(
        Long id,
        String folio,
        long branchId,
        LocalDate batchDate,
        Long productionId,
        List<BatchInput> inputs,
        List<BatchOutput> outputs,
        String notes,
        String createdBy,
        String createdRole) {

    public BakeryBatch {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }
}
