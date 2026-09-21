package com.mybusinesssilva.inventory.domain.port.out;

import com.mybusinesssilva.inventory.domain.model.InventoryMovement;
import com.mybusinesssilva.inventory.domain.model.StockLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de inventario (schema del tenant).
 */
public interface InventoryRepository {

    Optional<StockLevel> findStock(long productId, long branchId);

    /**
     * Ajusta la existencia de forma atómica y devuelve la cantidad resultante.
     * Crea el registro de stock si no existe.
     *
     * @param delta cantidad a sumar (puede ser negativa)
     */
    BigDecimal adjustQuantity(long productId, long branchId, BigDecimal delta);

    void setMinQuantity(long productId, long branchId, BigDecimal minQuantity);

    void recordMovement(InventoryMovement movement);

    List<InventoryMovement> kardex(long productId);

    /** Existencias que están en o por debajo de su stock mínimo (alertas). */
    List<StockLevel> findBelowMinimum();
}
