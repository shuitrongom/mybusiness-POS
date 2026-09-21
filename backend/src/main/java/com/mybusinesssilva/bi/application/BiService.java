package com.mybusinesssilva.bi.application;

import com.mybusinesssilva.bi.domain.model.DashboardSummary;
import com.mybusinesssilva.bi.domain.model.ProductRanking;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Casos de uso de Business Intelligence: dashboards, rankings y análisis ABC.
 *
 * <p>Consulta por agregación las tablas de ventas del tenant en curso. Las consultas usan solo
 * ventas COMPLETED. El análisis ABC clasifica los productos por su contribución acumulada al
 * ingreso (regla de Pareto: A ≈ 80% del ingreso, B hasta 95%, C el resto).
 */
@Service
public class BiService {

    private final JdbcClient jdbc;

    public BiService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Resumen del día: número de ventas, total, ticket promedio y unidades vendidas.
     */
    public DashboardSummary todaySummary() {
        long salesCount = jdbc.sql("""
                SELECT COALESCE(count(*),0) FROM sale
                WHERE status='COMPLETED' AND created_at::date = current_date
                """).query(Long.class).single();

        BigDecimal salesTotal = jdbc.sql("""
                SELECT COALESCE(sum(total),0) FROM sale
                WHERE status='COMPLETED' AND created_at::date = current_date
                """).query(BigDecimal.class).single();

        BigDecimal itemsSold = jdbc.sql("""
                SELECT COALESCE(sum(sl.quantity),0) FROM sale_line sl
                JOIN sale s ON s.id = sl.sale_id
                WHERE s.status='COMPLETED' AND s.created_at::date = current_date
                """).query(BigDecimal.class).single();

        BigDecimal avgTicket = salesCount == 0
                ? BigDecimal.ZERO
                : salesTotal.divide(BigDecimal.valueOf(salesCount), 2, RoundingMode.HALF_UP);

        return new DashboardSummary(
                java.time.LocalDate.now().toString(),
                salesCount, salesTotal, avgTicket, itemsSold);
    }

    /**
     * Productos más vendidos por ingreso en los últimos {@code days} días.
     */
    public List<ProductRanking> topProducts(int days, int limit) {
        return jdbc.sql("""
                SELECT p.id AS product_id, p.name,
                       COALESCE(sum(sl.quantity),0) AS quantity,
                       COALESCE(sum(sl.line_total),0) AS revenue,
                       COALESCE(sum(sl.line_total - (p.cost * sl.quantity)),0) AS profit
                FROM sale_line sl
                JOIN sale s ON s.id = sl.sale_id
                JOIN product p ON p.id = sl.product_id
                WHERE s.status='COMPLETED' AND s.created_at >= now() - make_interval(days => :days)
                GROUP BY p.id, p.name
                ORDER BY revenue DESC
                LIMIT :limit
                """)
                .param("days", days)
                .param("limit", limit)
                .query((rs, n) -> new ProductRanking(
                        rs.getLong("product_id"), rs.getString("name"),
                        rs.getBigDecimal("quantity"), rs.getBigDecimal("revenue"),
                        rs.getBigDecimal("profit"), null))
                .list();
    }

    /**
     * Análisis ABC de inventario por ingreso en los últimos {@code days} días.
     * Clasifica cada producto en A/B/C según el porcentaje acumulado de ingreso.
     */
    public List<ProductRanking> abcAnalysis(int days) {
        List<ProductRanking> ranked = jdbc.sql("""
                SELECT p.id AS product_id, p.name,
                       COALESCE(sum(sl.quantity),0) AS quantity,
                       COALESCE(sum(sl.line_total),0) AS revenue,
                       COALESCE(sum(sl.line_total - (p.cost * sl.quantity)),0) AS profit
                FROM sale_line sl
                JOIN sale s ON s.id = sl.sale_id
                JOIN product p ON p.id = sl.product_id
                WHERE s.status='COMPLETED' AND s.created_at >= now() - make_interval(days => :days)
                GROUP BY p.id, p.name
                ORDER BY revenue DESC
                """)
                .param("days", days)
                .query((rs, n) -> new ProductRanking(
                        rs.getLong("product_id"), rs.getString("name"),
                        rs.getBigDecimal("quantity"), rs.getBigDecimal("revenue"),
                        rs.getBigDecimal("profit"), null))
                .list();

        BigDecimal totalRevenue = ranked.stream()
                .map(ProductRanking::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalRevenue.signum() == 0) {
            return ranked;
        }

        // La clase de cada producto se decide por el porcentaje acumulado ANTES de incluirlo:
        // mientras el acumulado previo no rebase el umbral, el producto pertenece a esa clase.
        // Así el producto que cruza el 80% sigue siendo 'A' (aunque su acumulado lo supere).
        List<ProductRanking> classified = new ArrayList<>();
        BigDecimal cumulativeBefore = BigDecimal.ZERO;
        for (ProductRanking r : ranked) {
            BigDecimal pctBefore = cumulativeBefore.multiply(BigDecimal.valueOf(100))
                    .divide(totalRevenue, 2, RoundingMode.HALF_UP);
            String abc = pctBefore.compareTo(BigDecimal.valueOf(80)) < 0 ? "A"
                    : pctBefore.compareTo(BigDecimal.valueOf(95)) < 0 ? "B" : "C";
            classified.add(new ProductRanking(r.productId(), r.name(), r.quantity(),
                    r.revenue(), r.profit(), abc));
            cumulativeBefore = cumulativeBefore.add(r.revenue());
        }
        return classified;
    }
}
