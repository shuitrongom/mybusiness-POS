package com.mybusinesssilva.inventory.rawmaterial.domain.port.out;

import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterial;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterialMovementType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto de salida del subsistema de materia prima. Implementado con JDBC sobre el
 * schema del tenant en curso. Mantiene la contabilidad de insumos separada del pan.
 */
public interface RawMaterialRepository {

    long insert(RawMaterial m);

    void update(long id, RawMaterial m);

    void setActive(long id, boolean active);

    Optional<RawMaterial> find(long id);

    /**
     * Existe un insumo ACTIVO con ese nombre (case-insensitive), excluyendo el id dado.
     * Barrera previa al INSERT para dar un 422 legible (el índice parcial es la barrera dura).
     */
    boolean existsActiveName(String name, Long excludeId);

    /** Existe un insumo con ese SKU, excluyendo el id dado. */
    boolean existsSku(String sku, Long excludeId);

    /**
     * Upsert atómico de existencia (entradas/ajustes). Materializa la fila
     * {@code (raw_material_id, branch_id)} si no existe y devuelve el balance resultante.
     */
    BigDecimal adjustQuantity(long rmId, long branchId, BigDecimal signedQty);

    /**
     * Descuento estricto atómico: {@code UPDATE … WHERE quantity >= :qty}. Devuelve
     * {@code true} solo si descontó una fila; {@code false} si no hay fila o no alcanza.
     * No crea fila (si no existe stock, devuelve false). Barrera dura de concurrencia.
     */
    boolean deductIfEnough(long rmId, long branchId, BigDecimal qty);

    /** Inserta un renglón del kardex con el balance y el costo congelado. */
    void recordMovement(long rmId, long branchId, RawMaterialMovementType type,
                        BigDecimal signedQty, BigDecimal balanceAfter, BigDecimal unitCost,
                        String reason, String reference, String actor);

    /** Catálogo + existencia por sucursal (todos los insumos, para el panel admin). */
    List<Map<String, Object>> listWithStock(Long branchId);

    /**
     * Insumos disponibles para una tanda: {@code active = TRUE AND quantity > 0} en la
     * sucursal. No se puede tomar lo que no hay.
     */
    List<Map<String, Object>> listAvailableForBatch(long branchId);

    /** Existencia actual de un insumo en una sucursal (0 si no hay registro). */
    BigDecimal stock(long rmId, long branchId);

    /** Kardex de un insumo por rango (mapas). */
    List<Map<String, Object>> movements(long rmId, LocalDate from, LocalDate to);

    /** {@code true} si el insumo tiene al menos un movimiento en el kardex. */
    boolean hasMovements(long rmId);

    /** {@code true} si el insumo tiene existencia distinta de cero en alguna sucursal. */
    boolean hasAnyStock(long rmId);

    /**
     * Borrado físico de un insumo VIRGEN (sin movimientos ni existencia). Borra primero sus
     * filas de {@code raw_material_stock} (todas en 0) y luego la fila de {@code raw_material}
     * (orden por FK). No toca el kardex: el service solo lo invoca cuando no hay movimientos.
     */
    void hardDelete(long rmId);
}
