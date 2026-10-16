package com.mybusinesssilva.inventory.bakery.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Entrada de producción de pan (cabecera + renglones).
 *
 * @param id             identificador (null antes de persistir)
 * @param folio          folio ('PROD-'||id), asignado tras el insert
 * @param branchId       sucursal
 * @param statusCode     estatus del catálogo (default PRODUCED)
 * @param productionDate fecha natural de producción (America/Mexico_City)
 * @param totalQty       cantidad total producida
 * @param totalValue     valor total
 * @param notes          notas
 * @param createdBy      actor que registró la producción
 * @param createdRole    rol operativo del actor
 * @param createdAt      fecha/hora de registro
 * @param lines          renglones de producción
 */
public record BakeryProduction(
        Long id,
        String folio,
        long branchId,
        String statusCode,
        LocalDate productionDate,
        BigDecimal totalQty,
        BigDecimal totalValue,
        String notes,
        String createdBy,
        String createdRole,
        Instant createdAt,
        List<BakeryProductionLine> lines) {

    public BakeryProduction {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
