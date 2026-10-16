package com.mybusinesssilva.platform.billing;

import com.mybusinesssilva.bi.domain.model.TimeSeriesPoint;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Casos de uso adicionales de Business Intelligence de PLATAFORMA para el Super Admin: series
 * mensuales de negocios creados e ingresos, y pruebas por vencer.
 *
 * <p>Complementa a {@link SuperAdminBiController} (que conserva su resumen, mezcla de planes y
 * ventas recientes) sobre el schema global {@code admin}. Las fechas se guardan en UTC
 * (TIMESTAMPTZ) y las series mensuales se agrupan en hora local de México
 * ({@code America/Mexico_City}).
 */
@Service
public class SuperAdminBiService {

    /** Zona horaria de negocio: las fechas se guardan en UTC y se agrupan en hora local de México. */
    private static final String TZ = "America/Mexico_City";

    private final JdbcClient jdbc;

    public SuperAdminBiService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Serie mensual de negocios creados en los últimos {@code months} meses, agrupada por mes
     * local de México. El campo {@code revenue} del punto es cero (no aplica); {@code tickets}
     * lleva el número de altas.
     */
    public List<TimeSeriesPoint> businessesByMonth(int months) {
        return jdbc.sql("""
                SELECT to_char(date_trunc('month', created_at AT TIME ZONE :tz), 'YYYY-MM') AS m,
                       count(*)                                                             AS tickets
                FROM admin.business
                WHERE created_at >= now() - make_interval(months => :months)
                GROUP BY m
                ORDER BY m
                """)
                .param("tz", TZ)
                .param("months", months)
                .query((rs, n) -> new TimeSeriesPoint(
                        rs.getString("m"),
                        rs.getLong("tickets"),
                        BigDecimal.ZERO))
                .list();
    }

    /**
     * Serie mensual de ingresos del Super Admin en los últimos {@code months} meses, agrupada por
     * mes local de México. {@code tickets} lleva el número de ventas del mes y {@code revenue} el
     * monto cobrado.
     */
    public List<TimeSeriesPoint> revenueByMonth(int months) {
        return jdbc.sql("""
                SELECT to_char(date_trunc('month', created_at AT TIME ZONE :tz), 'YYYY-MM') AS m,
                       count(*)                   AS tickets,
                       COALESCE(sum(amount),0)    AS revenue
                FROM admin.superadmin_sale
                WHERE created_at >= now() - make_interval(months => :months)
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
     * Negocios en prueba cuyo periodo termina dentro de los próximos {@code days} días, ordenados
     * por fecha de vencimiento más próxima.
     */
    public List<TrialEnding> trialsEndingSoon(int days) {
        return jdbc.sql("""
                SELECT id                                                                 AS id,
                       name                                                               AS name,
                       to_char(trial_ends_at AT TIME ZONE :tz, 'YYYY-MM-DD"T"HH24:MI:SS') AS ends
                FROM admin.business
                WHERE status = 'TRIAL'
                  AND trial_ends_at IS NOT NULL
                  AND trial_ends_at BETWEEN now() AND now() + make_interval(days => :days)
                ORDER BY trial_ends_at
                """)
                .param("tz", TZ)
                .param("days", days)
                .query((rs, n) -> new TrialEnding(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("ends")))
                .list();
    }
}
