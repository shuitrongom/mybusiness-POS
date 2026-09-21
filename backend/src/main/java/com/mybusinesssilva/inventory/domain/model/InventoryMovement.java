package com.mybusinesssilva.inventory.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Movimiento de inventario (renglón del kardex).
 *
 * @param id           identificador
 * @param productId    producto
 * @param branchId     sucursal
 * @param type         tipo de movimiento
 * @param quantity     cantidad (positiva entra, negativa sale)
 * @param balanceAfter existencia resultante tras el movimiento
 * @param reason       motivo (para ajustes)
 * @param reference    referencia (folio de venta/compra/traspaso)
 * @param actor        quién realizó el movimiento
 * @param createdAt    fecha/hora
 */
public record InventoryMovement(
        Long id,
        Long productId,
        Long branchId,
        MovementType type,
        BigDecimal quantity,
        BigDecimal balanceAfter,
        String reason,
        String reference,
        String actor,
        Instant createdAt) {
}
