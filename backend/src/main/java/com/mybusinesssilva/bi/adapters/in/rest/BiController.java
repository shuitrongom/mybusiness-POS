package com.mybusinesssilva.bi.adapters.in.rest;

import com.mybusinesssilva.bi.application.AnalyticsService;
import com.mybusinesssilva.bi.application.BiService;
import com.mybusinesssilva.bi.application.insight.Insight;
import com.mybusinesssilva.bi.application.insight.InsightEngine;
import com.mybusinesssilva.bi.domain.model.DashboardSummary;
import com.mybusinesssilva.bi.domain.model.PaymentMix;
import com.mybusinesssilva.bi.domain.model.PeriodComparison;
import com.mybusinesssilva.bi.domain.model.ProductRanking;
import com.mybusinesssilva.bi.domain.model.PurchaseSuggestion;
import com.mybusinesssilva.bi.domain.model.TimeSeriesPoint;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de Business Intelligence. Requieren el módulo {@code bi} habilitado.
 */
@RestController
@RequestMapping("/api/v1/bi")
@PreAuthorize("@moduleAccess.canUse('bi')")
public class BiController {

    private final BiService biService;
    private final AnalyticsService analyticsService;
    private final InsightEngine insightEngine;

    public BiController(BiService biService, AnalyticsService analyticsService,
            InsightEngine insightEngine) {
        this.biService = biService;
        this.analyticsService = analyticsService;
        this.insightEngine = insightEngine;
    }

    @GetMapping("/dashboard/today")
    public DashboardSummary today() {
        return biService.todaySummary();
    }

    @GetMapping("/products/top")
    public List<ProductRanking> topProducts(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return biService.topProducts(days, limit);
    }

    @GetMapping("/products/abc")
    public List<ProductRanking> abc(@RequestParam(defaultValue = "90") int days) {
        return biService.abcAnalysis(days);
    }

    @GetMapping("/purchase-suggestions")
    public List<PurchaseSuggestion> purchaseSuggestions(
            @RequestParam(defaultValue = "15") int horizonDays) {
        return analyticsService.purchaseSuggestions(horizonDays);
    }

    @GetMapping("/anomalies/cash")
    public List<Map<String, Object>> cashAnomalies(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "0.2") double threshold) {
        return analyticsService.cashAnomalies(days, threshold);
    }

    @GetMapping("/sales/series")
    public List<TimeSeriesPoint> salesSeries(@RequestParam(defaultValue = "30") int days) {
        return biService.salesByDay(days);
    }

    @GetMapping("/sales/series/monthly")
    public List<TimeSeriesPoint> salesSeriesMonthly(
            @RequestParam(defaultValue = "12") int months) {
        return biService.salesByMonth(months);
    }

    @GetMapping("/comparison")
    public PeriodComparison comparison() {
        return biService.periodComparison();
    }

    @GetMapping("/payments/mix")
    public List<PaymentMix> paymentsMix(@RequestParam(defaultValue = "30") int days) {
        return biService.paymentMix(days);
    }

    /**
     * Insights del negocio en español: arma un mapa con las métricas (resumen de hoy, serie diaria,
     * comparativa de periodos, mezcla de pago, top de productos y análisis ABC) y delega su
     * interpretación al {@link InsightEngine}.
     */
    @GetMapping("/insights")
    public List<Insight> insights() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("today", biService.todaySummary());
        metrics.put("series", biService.salesByDay(30));
        metrics.put("comparison", biService.periodComparison());
        metrics.put("paymentMix", biService.paymentMix(30));
        metrics.put("topProducts", biService.topProducts(30, 10));
        metrics.put("abc", biService.abcAnalysis(90));
        return insightEngine.generate("negocio", metrics);
    }
}
