package com.smartstudy.planner;

import java.time.Instant;
import java.time.LocalDate;

public record PlanSummaryResponse(
        Long planId,
        LocalDate startDate,
        LocalDate endDate,
        Instant generatedAt,
        int totalPlannedMinutes,
        int sessionCount,
        String strategySummary,
        PlanStatus status) {

    public static PlanSummaryResponse from(StudyPlan plan) {
        return new PlanSummaryResponse(plan.getId(), plan.getStartDate(), plan.getEndDate(), plan.getGeneratedAt(),
                plan.getTotalPlannedMinutes(), plan.getSessionCount(), plan.getStrategySummary(), plan.getStatus());
    }
}
