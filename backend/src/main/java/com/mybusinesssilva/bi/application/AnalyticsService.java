package com.mybusinesssilva.bi.application;

import com.mybusinesssilva.bi.domain.model.PurchaseSuggestion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Analítica avanzada: predicción de demanda, sugerencia de compra y detección de anomalías.
 *
 * <p>La predicción de demanda parte del histórico de ventas reciente (demanda diaria promedio).
 * La sugerencia de compra combina existencia, stock mínimo y demanda proyectada para un horizonte.
 * La detección de anomalías señala cajeros con proporción atípica de cancelaciones, útil para
 * revisar posibles mermas o fraude en caja.
 */
@Service
public class AnalyticsService {

    private final JdbcClient jdbc;

    public AnalyticsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Demanda diaria promedio de un producto en los últimos {@code days} días.
     */
    public BigDecimal averageDailyDemand(long productId, int days) {
        BigDecimal soldQty = jdbc.sql("""
                SELECT COALESCE(sum(sl.quantity),0) FROM sale_line sl
                JOIN sale s ON s.id = sl.sale_id
                WHERE s.status='COMPLETED' AND sl.product_id = :pid
                  AND s.created_at >= now() - make_interval(days => :days)
                """)
                .param("pid", productId)
                .param("days", days)
                .query(BigDecimal.class)
                .single();
        return soldQty.divide(BigDecimal.valueOf(Math.max(1, days)), 3, RoundingMode.HALF_UP);
    }

    /**
     * Sugerencias de compra: productos en o por debajo del mínimo, con cantidad sugerida que cubre
     * el mínimo y la demanda proyectada del horizonte indicado.
     *
     * @param horizonDays días de demanda que se quieren cubrir con la compra
     */
    public List<PurchaseSuggestion> purchaseSuggestions(int horizonDays) {
        // Productos con existencia <= mínimo (candidatos a reabastecer).
        List<long[]> candidates = jdbc.sql("""
                SELECT product_id, quantity, min_quantity
                FROM inventory_stock
                WHERE min_quantity > 0 AND quantity <= min_quantity
                """)
                .query((rs, n) -> new long[]{rs.getLong("product_id")})
                .list();

        java.util.ArrayList<PurchaseSuggestion> suggestions = new java.util.ArrayList<>();
        for (long[] c : candidates) {
            long productId = c[0];
            var info = jdbc.sql("""
                    SELECT p.name AS name, st.quantity AS qty, st.min_quantity AS minq
                    FROM inventory_stock st JOIN product p ON p.id = st.product_id
                    WHERE st.product_id = :pid
                    LIMIT 1
                    """)
                    .param("pid", productId)
                    .query((rs, n) -> Map.of(
                            "name", rs.getString("name"),
                            "qty", rs.getBigDecimal("qty"),
                            "minq", rs.getBigDecimal("minq")))
                    .single();

            BigDecimal current = (BigDecimal) info.get("qty");
            BigDecimal min = (BigDecimal) info.get("minq");
            BigDecimal avgDemand = averageDailyDemand(productId, 30);

            // Cantidad objetivo = mínimo + demanda del horizonte; sugerido = objetivo - existencia.
            BigDecimal target = min.add(avgDemand.multiply(BigDecimal.valueOf(horizonDays)));
            BigDecimal suggested = target.subtract(current).max(BigDecimal.ZERO)
                    .setScale(0, RoundingMode.CEILING);

            suggestions.add(new PurchaseSuggestion(
                    productId, (String) info.get("name"), current, min, avgDemand, suggested));
        }
        return suggestions;
    }

    /**
     * Detección de anomalías en caja: cajeros cuya proporción de ventas canceladas supera el
     * umbral indicado en el periodo dado. Señal para revisar posibles mermas o fraude.
     *
     * @param days      periodo a analizar
     * @param threshold proporción (0..1) de cancelaciones a partir de la cual se marca anomalía
     */
    public List<Map<String, Object>> cashAnomalies(int days, double threshold) {
        return jdbc.sql("""
                SELECT cashier,
                       count(*) FILTER (WHERE status='VOIDED') AS voided,
                       count(*) AS total,
                       ROUND(
                         count(*) FILTER (WHERE status='VOIDED')::numeric
                         / NULLIF(count(*),0), 4) AS void_ratio
                FROM sale
                WHERE created_at >= now() - make_interval(days => :days)
                GROUP BY cashier
                HAVING count(*) > 0
                   AND count(*) FILTER (WHERE status='VOIDED')::numeric / NULLIF(count(*),0) >= :thr
                ORDER BY void_ratio DESC
                """)
                .param("days", days)
                .param("thr", threshold)
                .query((rs, n) -> Map.<String, Object>of(
                        "cashier", rs.getString("cashier") == null ? "" : rs.getString("cashier"),
                        "voided", rs.getLong("voided"),
                        "total", rs.getLong("total"),
                        "voidRatio", rs.getBigDecimal("void_ratio")))
                .list();
    }
}
