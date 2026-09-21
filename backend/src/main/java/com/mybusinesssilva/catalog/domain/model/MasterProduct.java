package com.mybusinesssilva.catalog.domain.model;

/**
 * Producto del catálogo maestro compartido. Sirve como sugerencia al dar de alta productos en
 * cualquier negocio, y crece con el uso.
 *
 * @param id           identificador
 * @param barcode      código de barras (puede ser nulo para productos a granel)
 * @param name         nombre
 * @param brand        marca
 * @param category     categoría
 * @param unit         unidad común
 * @param satProdServ  clave SAT de producto/servicio sugerida
 * @param satUnit      clave SAT de unidad sugerida
 */
public record MasterProduct(
        Long id,
        String barcode,
        String name,
        String brand,
        String category,
        String unit,
        String satProdServ,
        String satUnit) {
}
