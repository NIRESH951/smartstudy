package com.smartstudy.planner;

import java.time.LocalDate;
import java.util.List;

/** Sessions of one local calendar day with planned and completed minutes. */
public record DayPlanResponse(
        LocalDate date,
        int plannedMinutes,
        int completedMinutes,
        List<SessionResponse> sessions) {
}
