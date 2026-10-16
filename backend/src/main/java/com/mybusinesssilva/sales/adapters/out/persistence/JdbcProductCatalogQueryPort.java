package com.mybusinesssilva.sales.adapters.out.persistence;

import com.mybusinesssilva.sales.domain.port.out.ProductCatalogQueryPort;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del puerto de consulta de catálogo, sobre el schema del tenant en curso.
 * La RLS aísla por tenant automáticamente (product vive en el mismo schema que sale).
 */
@Repository
public class JdbcProductCatalogQueryPort implements ProductCatalogQueryPort {

    private final JdbcClient jdbc;

    public JdbcProductCatalogQueryPort(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<Long> enforceStockProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>(productIds);
        return new HashSet<>(jdbc.sql(
                "SELECT id FROM product WHERE id IN (:ids) AND enforce_stock = TRUE")
                .param("ids", ids)
                .query(Long.class)
                .list());
    }
}
