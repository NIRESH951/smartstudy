package com.smartstudy.analytics;

import java.time.LocalDate;
import java.util.List;

import com.smartstudy.common.CurrentUser;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final CurrentUser currentUser;

    public AnalyticsController(AnalyticsService analyticsService, CurrentUser currentUser) {
        this.analyticsService = analyticsService;
        this.currentUser = currentUser;
    }

    @GetMapping("/overview")
    public DashboardOverviewResponse overview() {
        return analyticsService.overview(currentUser.id());
    }

    @GetMapping("/weekly")
    public WeeklyAnalyticsResponse weekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return analyticsService.weekly(currentUser.id(), date);
    }

    @GetMapping("/subjects")
    public List<SubjectAnalyticsResponse> subjects() {
        return analyticsService.subjects(currentUser.id());
    }

    @GetMapping("/productivity")
    public ProductivityResponse productivity(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.productivity(currentUser.id(), days);
    }
}
