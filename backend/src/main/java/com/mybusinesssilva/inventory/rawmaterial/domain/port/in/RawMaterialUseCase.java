package com.mybusinesssilva.inventory.rawmaterial.domain.port.in;

import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Puerto de entrada (casos de uso) del catálogo y kardex de materia prima.
 * Reservado a administración de inventario (OWNER/ADMIN/SUPERVISOR); el panadero
 * solo CONSUME insumos vía la tanda, nunca administra el catálogo por aquí.
 */
public interface RawMaterialUseCase {

    /** Alta de un insumo. @return id creado. */
    long create(RawMaterial m);

    /** Edición de un insumo existente. */
    void update(long id, RawMaterial m);

    /** Activa o desactiva un insumo. */
    void setActive(long id, boolean active);

    /** Catálogo de insumos + existencia por sucursal (mapas para el panel). */
    List<Map<String, Object>> list(Long branchId);

    /**
     * Reposición de existencia de un insumo (compra): movimiento {@code PURCHASE_RM}.
     * El panadero NO repone; solo el admin.
     */
    void registerEntry(long id, long branchId, BigDecimal qty, BigDecimal unitCost, String actor);

    /** Kardex de un insumo por rango (mapas para el panel). */
    List<Map<String, Object>> movements(long id, LocalDate from, LocalDate to);
}
