package com.mybusinesssilva.sales.domain.port.out;

import java.util.Collection;
import java.util.Set;

/**
 * Puerto de consulta de solo lectura del catálogo de productos, usado por el
 * dominio de ventas
 * para resolver qué productos de una venta tienen control duro de existencia
 * (no-sobreventa)
 * sin acoplar el modelo {@code SaleLine} a la forma en que se decide esa regla.
 *
 * <p>
 * La regla de no-sobreventa depende del giro: en panadería todos los productos
 * del catálogo
 * la aplican; en los demás giros solo los marcados con
 * {@code enforce_stock = TRUE}. El adaptador
 * resuelve esa distinción; el dominio solo pregunta "¿cuáles de estos ids
 * controlan existencia?".
 */
public interface ProductCatalogQueryPort {

    /**
     * @param productIds ids de producto a consultar
     * @return el subconjunto de {@code productIds} sujetos a no-sobreventa (control
     *         duro de
     *         existencia) en el tenant actual; conjunto vacío si {@code productIds}
     *         es nulo o vacío
     */
    Set<Long> enforceStockProductIds(Collection<Long> productIds);
}
