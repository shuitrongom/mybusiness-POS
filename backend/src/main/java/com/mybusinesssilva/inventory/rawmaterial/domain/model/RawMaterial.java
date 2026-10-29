package com.mybusinesssilva.inventory.rawmaterial.domain.model;

import java.math.BigDecimal;

/**
 * Insumo de materia prima del giro panadería (harina, azúcar, etc.).
 *
 * <p>Vive en un subsistema AISLADO: estas entidades referencian
 * {@code raw_material(id)}, nunca {@code product(id)}, por lo que un insumo jamás
 * aparece en el POS, el catálogo de ventas ni las vistas de pan.
 *
 * @param id          identificador (null antes de persistir)
 * @param sku         clave opcional
 * @param name        nombre del insumo
 * @param unit        unidad ('kg' | 'litro' | 'pieza')
 * @param unitCost    costo por unidad (congelado al consumir en una tanda)
 * @param minQuantity umbral de alerta (informativo)
 * @param active      si está activo en el catálogo
 */
public record RawMaterial(
        Long id,
        String sku,
        String name,
        String unit,
        BigDecimal unitCost,
        BigDecimal minQuantity,
        boolean active) {
}
