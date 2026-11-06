package com.smartstudy.planner;

import java.time.LocalDate;
import java.util.List;

/** GET /api/v1/planner/today */
public record TodayResponse(
        LocalDate date,
        String timeZone,
        int plannedMinutes,
        int completedMinutes,
        double completionPercent,
        int sessionsPlanned,
        int sessionsCompleted,
        int sessionsMissed,
        List<SessionResponse> sessions,
        List<RecommendationResponse> recommendations,
        PlanSummaryResponse activePlan) {
}
