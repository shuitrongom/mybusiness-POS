package com.mybusinesssilva.sales.domain.port.out;

import com.mybusinesssilva.sales.domain.model.Sale;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de ventas (schema del tenant).
 */
public interface SaleRepository {

    Sale insert(Sale sale);

    boolean existsById(long id);

    /** Busca una venta por su clave de idempotencia (para deduplicar sincronización offline). */
    Optional<Long> findIdByIdempotencyKey(String idempotencyKey);

    void markVoided(long saleId);

    /** @return el estado actual de la venta (COMPLETED, VOIDED, QUOTE) o vacío si no existe. */
    Optional<String> statusOf(long saleId);

    /** @return la sucursal de la venta. */
    Optional<Long> branchOf(long saleId);

    /** @return los renglones de una venta (para reponer inventario en cancelaciones/devoluciones). */
    List<SaleLineRow> linesOf(long saleId);

    /**
     * Proyección de un renglón para reposición de inventario.
     *
     * @param productId producto
     * @param quantity  cantidad vendida
     */
    record SaleLineRow(long productId, BigDecimal quantity) {
    }
}
