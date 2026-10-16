package com.mybusinesssilva.sales.domain.port.out;

import java.util.Collection;
import java.util.Set;

/**
 * Puerto de consulta de solo lectura del catálogo de productos, usado por el dominio de ventas
 * para resolver qué productos de una venta tienen control duro de existencia
 * ({@code enforce_stock = TRUE}) sin acoplar el modelo {@code SaleLine} a esa bandera.
 */
public interface ProductCatalogQueryPort {

    /**
     * @param productIds ids de producto a consultar
     * @return el subconjunto de {@code productIds} cuyos productos tienen {@code enforce_stock = TRUE}
     *         en el catálogo del tenant; conjunto vacío si {@code productIds} es nulo o vacío
     */
    Set<Long> enforceStockProductIds(Collection<Long> productIds);
}
