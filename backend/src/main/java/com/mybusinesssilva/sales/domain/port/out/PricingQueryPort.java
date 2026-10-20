package com.mybusinesssilva.sales.domain.port.out;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Puerto de consulta de SOLO LECTURA de los datos de precio autoritativos de un
 * producto (lista 1..5 con respaldo al precio base, precio especial de cliente,
 * precio fijo de merma, costo, banderas de bloqueo/venta bajo costo y la tasa de
 * IVA informativa).
 *
 * <p>
 * Lo consume {@code SaleService} para recomputar el precio server-side (ALTA-1):
 * el backend es la autoridad del precio y no confía en el precio que manda el POS
 * para renglones de catálogo. No toca inventario; vive en el dominio de ventas y
 * su implementación lee {@code product}/{@code customer_price} (RLS aísla el
 * tenant).
 */
public interface PricingQueryPort {

    /**
     * Resuelve los datos de precio autoritativos de un conjunto de productos para
     * una lista de precio y (opcionalmente) un cliente con precios especiales. Solo
     * lectura.
     *
     * @param productIds  ids de producto a resolver
     * @param priceListId lista de precio elegida (1..5); fuera de rango cae a la 1
     * @param customerId  cliente con precios especiales, o {@code null} si no aplica
     * @return mapa {@code productId -> ProductPricing}; los ids inexistentes en
     *         catálogo NO aparecen en el mapa
     */
    Map<Long, ProductPricing> resolve(Collection<Long> productIds, short priceListId, Long customerId);

    /**
     * Datos de precio autoritativos de un producto.
     *
     * @param productId      id del producto
     * @param name           nombre de catálogo (para los mensajes de las guardas de
     *                       negocio; no se confía en la descripción cruda del front)
     * @param listPrice      precio de la lista elegida con respaldo al precio base
     *                       ({@code price})
     * @param specialPrice   precio especial pactado para el cliente ({@code null}
     *                       si no hay)
     * @param wastePrice     precio FIJO de merma del artículo
     * @param cost           costo del artículo (para la guarda de venta bajo costo)
     * @param blocked        {@code true} si el artículo está bloqueado (no operable)
     * @param allowBelowCost {@code true} si se permite vender por debajo del costo
     * @param taxRate        tasa de IVA informativa. RESERVADO: este alcance no lo
     *                       consume ({@code sale.tax} sigue en 0 y el dominio
     *                       {@code Sale} no modela IVA). Se expone a propósito para
     *                       un futuro desglose fiscal sin volver a tocar el puerto
     *                       ni el SELECT
     */
    record ProductPricing(long productId, String name, BigDecimal listPrice, BigDecimal specialPrice,
            BigDecimal wastePrice, BigDecimal cost, boolean blocked, boolean allowBelowCost,
            BigDecimal taxRate) {

        /**
         * Precio de venta autoritativo para una venta NORMAL: el especial de cliente si
         * existe (incluso 0, coherente con el front y el steering "no se opera con
         * precio 0"), si no el de lista.
         */
        public BigDecimal effectiveSalePrice() {
            return (specialPrice != null && specialPrice.signum() >= 0) ? specialPrice : listPrice;
        }
    }
}
