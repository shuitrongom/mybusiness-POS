package com.mybusinesssilva.inventory.bakery.adapters.out.persistence;

import com.mybusinesssilva.inventory.bakery.domain.port.out.BatchProductQueryPort;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Fuente del precio de ficha del pan para la tanda. {@code product.price} ES nullable
 * en el repo real, así que se normaliza a 0 en el borde con {@code COALESCE(price,0)}:
 * el puerto NUNCA devuelve null (evita NPE → 500 y hace cierto el 422 del precio 0).
 *
 * <p>El paso 2 "¿Qué salió?" lista SOLO panes: {@code WHERE active = TRUE AND
 * is_bakery = TRUE}. El flag {@code is_bakery} (columna desde V34, índice parcial
 * {@code idx_product_is_bakery}) ya quedó poblado por el data-fix en tenant_9, así que
 * el empaque (BO=Bolsa, Bolsa/25, Bolsa/50, PO=Polipapel) queda fuera: el panadero solo
 * ve panes. La materia prima vive aparte en {@code raw_material}, nunca en
 * {@code product}, así que su aislamiento no depende de este flag.
 */
@Repository
public class JdbcBatchProductQueryPort implements BatchProductQueryPort {

    private final JdbcClient jdbc;

    public JdbcBatchProductQueryPort(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BigDecimal productPrice(long productId) {
        return jdbc.sql("SELECT COALESCE(price, 0) FROM product WHERE id = :id")
                .param("id", productId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public List<Map<String, Object>> bakeryProductsWithPrice(long branchId) {
        return jdbc.sql("""
                SELECT id, name, unit, COALESCE(price, 0) AS entry_price
                FROM product
                WHERE active = TRUE AND is_bakery = TRUE
                ORDER BY name
                """)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", rs.getLong("id"));
                    row.put("name", rs.getString("name"));
                    row.put("unit", rs.getString("unit"));
                    BigDecimal price = rs.getBigDecimal("entry_price");
                    row.put("entryPrice", price);
                    row.put("priceReady", price != null && price.signum() > 0);
                    return row;
                })
                .list();
    }
}
