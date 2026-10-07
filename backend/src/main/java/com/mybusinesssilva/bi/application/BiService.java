package com.mybusinesssilva.bi.application;

import com.mybusinesssilva.bi.domain.model.DashboardSummary;
import com.mybusinesssilva.bi.domain.model.PaymentMix;
import com.mybusinesssilva.bi.domain.model.PeriodComparison;
import com.mybusinesssilva.bi.domain.model.PeriodDelta;
import com.mybusinesssilva.bi.domain.model.ProductRanking;
import com.mybusinesssilva.bi.domain.model.TimeSeriesPoint;
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

    /** Zona horaria de negocio: las ventas se guardan en UTC y se agrupan en hora local de México. */
    private static final String TZ = "America/Mexico_City";

    private final JdbcClient jdbc;

    public BiService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Resumen del día: número de ventas, total, ticket promedio y unidades vendidas.
     */
    public DashboardSummary todaySummary() {
        // Las ventas se guardan en UTC (TIMESTAMPTZ). Para "hoy" se compara la fecha en la zona
        // horaria local de México, no la del servidor: de lo contrario, una venta hecha por la
        // noche (que en UTC ya es el día siguiente) no contaría en el resumen del día.
        final String tz = TZ;

        long salesCount = jdbc.sql("""
                SELECT COALESCE(count(*),0) FROM sale
                WHERE status='COMPLETED'
                  AND (created_at AT TIME ZONE :tz)::date = (now() AT TIME ZONE :tz)::date
                """).param("tz", tz).query(Long.class).single();

        BigDecimal salesTotal = jdbc.sql("""
                SELECT COALESCE(sum(total),0) FROM sale
                WHERE status='COMPLETED'
                  AND (created_at AT TIME ZONE :tz)::date = (now() AT TIME ZONE :tz)::date
                """).param("tz", tz).query(BigDecimal.class).single();

        BigDecimal itemsSold = jdbc.sql("""
                SELECT COALESCE(sum(sl.quantity),0) FROM sale_line sl
                JOIN sale s ON s.id = sl.sale_id
                WHERE s.status='COMPLETED'
                  AND (s.created_at AT TIME ZONE :tz)::date = (now() AT TIME ZONE :tz)::date
                """).param("tz", tz).query(BigDecimal.class).single();

        BigDecimal avgTicket = salesCount == 0
                ? BigDecimal.ZERO
                : salesTotal.divide(BigDecimal.valueOf(salesCount), 2, RoundingMode.HALF_UP);

        return new DashboardSummary(
                java.time.LocalDate.now(java.time.ZoneId.of(tz)).toString(),
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

    /**
     * Serie diaria de ventas de los últimos {@code days} días, agrupada por fecha local de México.
     * Devuelve un punto por día con ventas registradas, en orden cronológico ascendente.
     */
    public List<TimeSeriesPoint> salesByDay(int days) {
        return jdbc.sql("""
                SELECT (created_at AT TIME ZONE :tz)::date AS d,
                       count(*)              AS tickets,
                       COALESCE(sum(total),0) AS revenue
                FROM sale
                WHERE status='COMPLETED'
                  AND created_at >= now() - make_interval(days => :days)
                GROUP BY d
                ORDER BY d
                """)
                .param("tz", TZ)
                .param("days", days)
                .query((rs, n) -> new TimeSeriesPoint(
                        rs.getString("d"),
                        rs.getLong("tickets"),
                        rs.getBigDecimal("revenue")))
                .list();
    }

    /**
     * Serie mensual de ventas de los últimos {@code months} meses, agrupada por mes local de
     * México. El campo {@code date} usa el formato {@code yyyy-MM}.
     */
    public List<TimeSeriesPoint> salesByMonth(int months) {
        return jdbc.sql("""
                SELECT to_char(date_trunc('month', created_at AT TIME ZONE :tz), 'YYYY-MM') AS m,
                       count(*)              AS tickets,
                       COALESCE(sum(total),0) AS revenue
                FROM sale
                WHERE status='COMPLETED'
                  AND created_at >= now() - make_interval(months => :months)
                GROUP BY m
                ORDER BY m
                """)
                .param("tz", TZ)
                .param("months", months)
                .query((rs, n) -> new TimeSeriesPoint(
                        rs.getString("m"),
                        rs.getLong("tickets"),
                        rs.getBigDecimal("revenue")))
                .list();
    }

    /**
     * Comparativa de periodos (todos agrupados en hora local de México): hoy vs ayer, semana
     * actual vs la anterior y mes actual vs el anterior. Cada {@link PeriodDelta} incluye la
     * variación porcentual respecto al periodo previo.
     */
    public PeriodComparison periodComparison() {
        BigDecimal today = sumBetween(
                "(created_at AT TIME ZONE :tz)::date = (now() AT TIME ZONE :tz)::date");
        BigDecimal yesterday = sumBetween(
                "(created_at AT TIME ZONE :tz)::date "
                        + "= ((now() AT TIME ZONE :tz)::date - 1)");

        BigDecimal thisWeek = sumBetween(
                "date_trunc('week', created_at AT TIME ZONE :tz) "
                        + "= date_trunc('week', now() AT TIME ZONE :tz)");
        BigDecimal lastWeek = sumBetween(
                "date_trunc('week', created_at AT TIME ZONE :tz) "
                        + "= date_trunc('week', (now() AT TIME ZONE :tz) - interval '7 days')");

        BigDecimal thisMonth = sumBetween(
                "date_trunc('month', created_at AT TIME ZONE :tz) "
                        + "= date_trunc('month', now() AT TIME ZONE :tz)");
        BigDecimal lastMonth = sumBetween(
                "date_trunc('month', created_at AT TIME ZONE :tz) "
                        + "= date_trunc('month', (now() AT TIME ZONE :tz) - interval '1 month')");

        return new PeriodComparison(
                delta(today, yesterday),
                delta(thisWeek, lastWeek),
                delta(thisMonth, lastMonth));
    }

    /**
     * Mezcla de ventas por método de pago en los últimos {@code days} días: número de operaciones
     * y monto total por método, de mayor a menor monto.
     */
    public List<PaymentMix> paymentMix(int days) {
        return jdbc.sql("""
                SELECT sp.method AS method,
                       count(*)              AS operations,
                       COALESCE(sum(sp.amount),0) AS amount
                FROM sale_payment sp
                JOIN sale s ON s.id = sp.sale_id
                WHERE s.status='COMPLETED'
                  AND s.created_at >= now() - make_interval(days => :days)
                GROUP BY sp.method
                ORDER BY amount DESC
                """)
                .param("days", days)
                .query((rs, n) -> new PaymentMix(
                        rs.getString("method"),
                        rs.getLong("operations"),
                        rs.getBigDecimal("amount")))
                .list();
    }

    /** Suma del total de ventas COMPLETED que cumplen la condición de fecha indicada. */
    private BigDecimal sumBetween(String dateCondition) {
        return jdbc.sql("""
                SELECT COALESCE(sum(total),0) FROM sale
                WHERE status='COMPLETED' AND %s
                """.formatted(dateCondition))
                .param("tz", TZ)
                .query(BigDecimal.class)
                .single();
    }

    /** Construye un {@link PeriodDelta} con variación porcentual (nula si el previo es cero). */
    private PeriodDelta delta(BigDecimal current, BigDecimal previous) {
        BigDecimal cur = current == null ? BigDecimal.ZERO : current;
        BigDecimal prev = previous == null ? BigDecimal.ZERO : previous;
        BigDecimal changePct = prev.signum() == 0
                ? null
                : cur.subtract(prev).multiply(BigDecimal.valueOf(100))
                        .divide(prev, 2, RoundingMode.HALF_UP);
        return new PeriodDelta(cur, prev, changePct);
    }
}
