package com.mybusinesssilva.sales.domain.port.out;

import com.mybusinesssilva.sales.domain.model.Sale;
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
}
