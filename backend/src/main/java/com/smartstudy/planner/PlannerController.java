package com.smartstudy.planner;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import com.smartstudy.common.CurrentUser;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/planner")
public class PlannerController {

    private final PlannerService plannerService;
    private final CurrentUser currentUser;

    public PlannerController(PlannerService plannerService, CurrentUser currentUser) {
        this.plannerService = plannerService;
        this.currentUser = currentUser;
    }

    /** Today's sessions, progress and the top three recommendations. */
    @GetMapping("/today")
    public TodayResponse today() {
        return plannerService.today(currentUser.id());
    }

    /** The Monday-Sunday week containing {@code date} (default: this week). */
    @GetMapping("/weekly")
    public CalendarResponse weekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return plannerService.weekly(currentUser.id(), date);
    }

    /** Any date range of up to 62 days (used by the monthly calendar). */
    @GetMapping("/calendar")
    public CalendarResponse calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return plannerService.calendar(currentUser.id(), from, to);
    }

    @GetMapping("/recommendations")
    public List<RecommendationResponse> recommendations(@RequestParam(defaultValue = "5") int limit) {
        return plannerService.recommendations(currentUser.id(), limit);
    }

    @GetMapping("/priorities")
    public PrioritiesResponse priorities() {
        return plannerService.priorities(currentUser.id());
    }

    @GetMapping("/active")
    public ResponseEntity<PlanSummaryResponse> active() {
        return plannerService.activePlan(currentUser.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/generate")
    public PlanResponse generate(@Valid @RequestBody(required = false) GeneratePlanRequest request) {
        return plannerService.generate(currentUser.id(), request);
    }

    @PostMapping("/regenerate")
    public PlanResponse regenerate() {
        return plannerService.regenerate(currentUser.id());
    }
}
