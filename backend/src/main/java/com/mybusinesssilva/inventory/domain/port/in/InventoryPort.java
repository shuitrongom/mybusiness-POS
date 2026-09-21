package com.mybusinesssilva.inventory.domain.port.in;

import com.mybusinesssilva.inventory.domain.model.MovementType;
import java.math.BigDecimal;

/**
 * Puerto de entrada del inventario, consumido por otros módulos (ventas, compras, devoluciones)
 * para aplicar movimientos sin acoplarse a las tablas de inventario.
 *
 * <p>Cada operación registra el movimiento en el kardex y actualiza la existencia de forma
 * consistente dentro de la transacción del caso de uso que la invoca.
 */
public interface InventoryPort {

    /**
     * Aplica un movimiento de inventario a un producto en una sucursal.
     *
     * @param productId producto
     * @param branchId  sucursal
     * @param type      tipo de movimiento
     * @param quantity  cantidad absoluta (siempre positiva); el signo lo determina el tipo
     * @param reference referencia (folio de venta/compra/traspaso)
     * @param actor     quién origina el movimiento
     * @return la existencia resultante tras aplicar el movimiento
     */
    BigDecimal applyMovement(long productId, long branchId, MovementType type,
                             BigDecimal quantity, String reference, String actor);
}
