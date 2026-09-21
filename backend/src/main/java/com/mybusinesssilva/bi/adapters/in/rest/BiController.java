package com.mybusinesssilva.bi.adapters.in.rest;

import com.mybusinesssilva.bi.application.AnalyticsService;
import com.mybusinesssilva.bi.application.BiService;
import com.mybusinesssilva.bi.domain.model.DashboardSummary;
import com.mybusinesssilva.bi.domain.model.ProductRanking;
import com.mybusinesssilva.bi.domain.model.PurchaseSuggestion;
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

    public BiController(BiService biService, AnalyticsService analyticsService) {
        this.biService = biService;
        this.analyticsService = analyticsService;
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
}
