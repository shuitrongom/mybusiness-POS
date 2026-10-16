package com.mybusinesssilva.bi.application.insight;

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
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Implementación de {@link InsightEngine} basada en reglas deterministas (sin IA externa).
 *
 * <p>Lee las métricas ya calculadas del {@code Map} y aplica reglas sencillas: variación
 * porcentual entre periodos (positivo/alerta), concentración del ingreso (análisis ABC),
 * proyección simple de ventas a partir de la serie diaria y alertas de caída. Todos los textos
 * se redactan en español.
 *
 * <p>Es tolerante a métricas ausentes: cada regla sólo emite insight si encuentra los datos que
 * necesita en el mapa.
 */
@Service
public class RuleBasedInsightEngine implements InsightEngine {

    @Override
    public List<Insight> generate(String contexto, Map<String, Object> metrics) {
        List<Insight> insights = new ArrayList<>();
        if (metrics == null) {
            return insights;
        }

        todayRule(metrics, insights);
        comparisonRules(metrics, insights);
        concentrationRule(metrics, insights);
        projectionRule(metrics, insights);
        paymentMixRule(metrics, insights);

        return insights;
    }

    /** Resumen de hoy: emite un insight informativo con las ventas del día. */
    private void todayRule(Map<String, Object> metrics, List<Insight> insights) {
        if (!(metrics.get("today") instanceof DashboardSummary summary)) {
            return;
        }
        BigDecimal total = summary.salesTotal() == null ? BigDecimal.ZERO : summary.salesTotal();
        if (summary.salesCount() == 0) {
            insights.add(new Insight("ventas", Insight.Severity.INFO,
                    "Sin ventas registradas hoy",
                    "Todavía no hay ventas en el día de hoy.",
                    "0"));
        } else {
            insights.add(new Insight("ventas", Insight.Severity.POSITIVO,
                    "Ventas de hoy",
                    "Llevas " + summary.salesCount() + " ventas por un total de $"
                            + total.toPlainString() + " el día de hoy.",
                    "$" + total.toPlainString()));
        }
    }

    /** Variación de hoy/semana/mes: >0 POSITIVO, caída fuerte ALERTA. */
    private void comparisonRules(Map<String, Object> metrics, List<Insight> insights) {
        if (!(metrics.get("comparison") instanceof PeriodComparison comparison)) {
            return;
        }
        addDeltaInsight(insights, "ventas_hoy", "las ventas de hoy", comparison.today());
        addDeltaInsight(insights, "ventas_semana", "las ventas de la semana", comparison.week());
        addDeltaInsight(insights, "ventas_mes", "las ventas del mes", comparison.month());
    }

    private void addDeltaInsight(List<Insight> insights, String tipo, String etiqueta,
            PeriodDelta delta) {
        if (delta == null || delta.changePct() == null) {
            return;
        }
        BigDecimal pct = delta.changePct();
        String pctTexto = pct.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
        if (pct.signum() > 0) {
            insights.add(new Insight(tipo, Insight.Severity.POSITIVO,
                    "Crecimiento en " + etiqueta,
                    "Suben " + pctTexto + " respecto al periodo anterior. ¡Buen momento!",
                    pctTexto));
        } else if (pct.compareTo(BigDecimal.valueOf(-20)) <= 0) {
            insights.add(new Insight(tipo, Insight.Severity.ALERTA,
                    "Caída en " + etiqueta,
                    "Bajan " + pctTexto + " respecto al periodo anterior. Conviene revisarlo.",
                    pctTexto));
        } else if (pct.signum() < 0) {
            insights.add(new Insight(tipo, Insight.Severity.INFO,
                    "Ligera baja en " + etiqueta,
                    "Bajan " + pctTexto + " respecto al periodo anterior.",
                    pctTexto));
        }
    }

    /** Concentración del ingreso: cuánto aporta el producto clase A líder. */
    @SuppressWarnings("unchecked")
    private void concentrationRule(Map<String, Object> metrics, List<Insight> insights) {
        if (!(metrics.get("abc") instanceof List<?> raw) || raw.isEmpty()) {
            return;
        }
        List<ProductRanking> abc = (List<ProductRanking>) raw;
        BigDecimal total = abc.stream()
                .map(ProductRanking::revenue)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            return;
        }
        ProductRanking leader = abc.get(0);
        if (leader.revenue() == null || leader.revenue().signum() <= 0) {
            return;
        }
        BigDecimal share = leader.revenue().multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
        String shareTexto = share.toPlainString() + "%";
        if (share.compareTo(BigDecimal.valueOf(40)) >= 0) {
            insights.add(new Insight("concentracion", Insight.Severity.ALERTA,
                    "Ingreso muy concentrado",
                    "'" + leader.name() + "' aporta " + shareTexto + " del ingreso. "
                            + "Depender de un solo producto es un riesgo.",
                    shareTexto));
        } else {
            insights.add(new Insight("concentracion", Insight.Severity.INFO,
                    "Producto líder",
                    "'" + leader.name() + "' es tu producto estrella con " + shareTexto
                            + " del ingreso.",
                    shareTexto));
        }
    }

    /** Proyección simple: promedio diario de la serie * días restantes del mes. */
    @SuppressWarnings("unchecked")
    private void projectionRule(Map<String, Object> metrics, List<Insight> insights) {
        if (!(metrics.get("series") instanceof List<?> raw) || raw.isEmpty()) {
            return;
        }
        List<TimeSeriesPoint> series = (List<TimeSeriesPoint>) raw;
        BigDecimal totalRevenue = series.stream()
                .map(TimeSeriesPoint::revenue)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalRevenue.signum() <= 0) {
            return;
        }
        BigDecimal avgDaily = totalRevenue.divide(
                BigDecimal.valueOf(series.size()), 2, RoundingMode.HALF_UP);
        BigDecimal projection = avgDaily.multiply(BigDecimal.valueOf(30))
                .setScale(2, RoundingMode.HALF_UP);
        insights.add(new Insight("proyeccion", Insight.Severity.INFO,
                "Proyección de ventas a 30 días",
                "Al ritmo actual (promedio de $" + avgDaily.toPlainString()
                        + " al día) venderías cerca de $" + projection.toPlainString()
                        + " en 30 días.",
                "$" + projection.toPlainString()));
    }

    /** Mezcla de pago: destaca el método dominante. */
    @SuppressWarnings("unchecked")
    private void paymentMixRule(Map<String, Object> metrics, List<Insight> insights) {
        if (!(metrics.get("paymentMix") instanceof List<?> raw) || raw.isEmpty()) {
            return;
        }
        List<PaymentMix> mix = (List<PaymentMix>) raw;
        BigDecimal total = mix.stream()
                .map(PaymentMix::amount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            return;
        }
        PaymentMix top = mix.stream()
                .filter(m -> m.amount() != null)
                .max((a, b) -> a.amount().compareTo(b.amount()))
                .orElse(null);
        if (top == null) {
            return;
        }
        BigDecimal share = top.amount().multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
        insights.add(new Insight("pagos", Insight.Severity.INFO,
                "Método de pago preferido",
                metodoEspanol(top.method()) + " concentra el " + share.toPlainString()
                        + "% del cobro.",
                share.toPlainString() + "%"));
    }

    private String metodoEspanol(String method) {
        if (method == null) {
            return "El pago";
        }
        return switch (method) {
            case "CASH" -> "El efectivo";
            case "CARD" -> "La tarjeta";
            case "TRANSFER" -> "La transferencia";
            case "VOUCHER" -> "El vale";
            default -> method;
        };
    }
}
