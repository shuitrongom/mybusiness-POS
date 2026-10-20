package com.mybusinesssilva.sales.adapters.out.persistence;

import com.mybusinesssilva.sales.domain.port.out.PricingQueryPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del puerto de consulta de precios, sobre el schema del
 * tenant en curso (RLS aísla el tenant: {@code product}/{@code customer_price}
 * viven en el mismo schema que {@code sale}). SOLO LECTURA: no toca inventario.
 *
 * <p>
 * Resuelve el precio de lista (1..5 con respaldo al precio base), el precio
 * especial del cliente (LEFT JOIN con {@code customer_price}; {@code NULL} si el
 * cliente es {@code null} o no hay precio pactado), el precio fijo de merma, el
 * costo y las banderas en UNA sola consulta.
 */
@Repository
public class JdbcPricingQueryPort implements PricingQueryPort {

    private final JdbcClient jdbc;

    public JdbcPricingQueryPort(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, ProductPricing> resolve(Collection<Long> productIds, short priceListId, Long customerId) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new HashSet<>(productIds);
        int priceList = priceListId <= 0 ? 1 : priceListId;
        Map<Long, ProductPricing> out = new HashMap<>();
        jdbc.sql("""
                SELECT p.id                            AS id,
                       p.name                          AS name,
                       CASE :priceList
                            WHEN 2 THEN (CASE WHEN p.price2 > 0 THEN p.price2 ELSE p.price END)
                            WHEN 3 THEN (CASE WHEN p.price3 > 0 THEN p.price3 ELSE p.price END)
                            WHEN 4 THEN (CASE WHEN p.price4 > 0 THEN p.price4 ELSE p.price END)
                            WHEN 5 THEN (CASE WHEN p.price5 > 0 THEN p.price5 ELSE p.price END)
                            ELSE p.price
                       END                             AS list_price,
                       cp.price                        AS special_price,
                       p.waste_price                   AS waste_price,
                       p.cost                          AS cost,
                       p.blocked                       AS blocked,
                       p.allow_below_cost              AS allow_below_cost,
                       p.tax_rate                      AS tax_rate
                FROM product p
                LEFT JOIN customer_price cp
                       ON cp.product_id = p.id
                      AND cp.customer_id = CAST(:customerId AS bigint)
                WHERE p.id IN (:ids)
                """)
                .param("priceList", priceList)
                .param("customerId", customerId)
                .param("ids", ids)
                .query((rs, n) -> new ProductPricing(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getBigDecimal("list_price"),
                        rs.getBigDecimal("special_price"),
                        rs.getBigDecimal("waste_price"),
                        rs.getBigDecimal("cost"),
                        rs.getBoolean("blocked"),
                        rs.getBoolean("allow_below_cost"),
                        rs.getBigDecimal("tax_rate")))
                .list()
                .forEach(pp -> out.put(pp.productId(), pp));
        return out;
    }
}
