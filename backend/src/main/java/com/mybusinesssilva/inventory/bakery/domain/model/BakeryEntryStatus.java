package com.mybusinesssilva.inventory.bakery.domain.model;

/**
 * Estatus de entrada de producción del catálogo extensible (PRODUCED, REPROCESS, DONATION, …).
 *
 * @param id        identificador
 * @param code      clave estable
 * @param label     etiqueta visible
 * @param sortOrder orden de presentación
 * @param active    si está activo
 */
public record BakeryEntryStatus(
        Long id,
        String code,
        String label,
        int sortOrder,
        boolean active) {
}
