package com.mybusinesssilva.inventory.bakery.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro de trazabilidad de una merma vendible, ligada por (saleId, productId).
 *
 * @param id         identificador
 * @param saleId     venta a la que pertenece la merma
 * @param productId  producto de pan vendido como merma
 * @param quantity   cantidad vendida como merma
 * @param entryPrice precio de entrada original (por unidad)
 * @param wastePrice precio de merma vendido (por unidad)
 * @param difference diferencia por unidad (entryPrice - wastePrice)
 * @param cashier    cajero que registró la merma
 * @param createdAt  fecha/hora
 */
public record WasteSale(
        Long id,
        long saleId,
        long productId,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal wastePrice,
        BigDecimal difference,
        String cashier,
        Instant createdAt) {
}
