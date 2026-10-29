package com.mybusinesssilva.inventory.bakery.domain.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Fuente del precio de ficha del pan para la tanda. El precio lo manda el artículo
 * (ficha), no la operación.
 */
public interface BatchProductQueryPort {

    /**
     * Precio de ficha del pan. NUNCA devuelve null: se implementa con
     * {@code SELECT COALESCE(price,0) …}, de modo que un pan sin precio capturado
     * devuelve 0 (que el servicio trata como "precio 0" → 422), no null → NPE → 500.
     *
     * @param productId pan existente
     * @return precio de ficha (0 si no está capturado); nunca null
     */
    BigDecimal productPrice(long productId);

    /**
     * Panes de la ficha para la tanda (misma lógica de "es pan" que el motor:
     * {@code WHERE active = TRUE}, SIN depender de {@code is_bakery}). {@code entryPrice}
     * se emite con {@code COALESCE(price,0)} (nunca null) y {@code priceReady = entryPrice > 0}.
     */
    List<Map<String, Object>> bakeryProductsWithPrice(long branchId);
}
