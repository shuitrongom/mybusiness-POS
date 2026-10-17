package com.mybusinesssilva.sales.adapters.out.persistence;

import com.mybusinesssilva.platform.security.ModuleAccessEvaluator;
import com.mybusinesssilva.sales.domain.port.out.ProductCatalogQueryPort;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del puerto de consulta de catálogo, sobre el schema del
 * tenant en curso.
 * La RLS aísla por tenant automáticamente (product vive en el mismo schema que
 * sale).
 *
 * <p>
 * No-sobreventa por giro: en giro panadería (módulo {@code bakery} activo)
 * <em>todos</em> los
 * productos del catálogo quedan sujetos a control duro de existencia, no solo
 * los marcados con
 * {@code enforce_stock = TRUE}. En los demás giros se mantiene la bandera por
 * producto. Así la
 * regla vive en el giro y no depende de marcar producto por producto (que nadie
 * hace en panadería).
 */
@Repository
public class JdbcProductCatalogQueryPort implements ProductCatalogQueryPort {

    private final JdbcClient jdbc;
    private final ModuleAccessEvaluator moduleAccess;

    public JdbcProductCatalogQueryPort(JdbcClient jdbc, ModuleAccessEvaluator moduleAccess) {
        this.jdbc = jdbc;
        this.moduleAccess = moduleAccess;
    }

    @Override
    public Set<Long> enforceStockProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>(productIds);
        // Giro panadería: todos los productos (que existan y estén activos en el
        // catálogo) quedan
        // bajo no-sobreventa. En otros giros, solo los marcados con enforce_stock =
        // TRUE.
        String sql = moduleAccess.canUse("bakery")
                ? "SELECT id FROM product WHERE id IN (:ids) AND active = TRUE"
                : "SELECT id FROM product WHERE id IN (:ids) AND enforce_stock = TRUE";
        return new HashSet<>(jdbc.sql(sql)
                .param("ids", ids)
                .query(Long.class)
                .list());
    }
}
