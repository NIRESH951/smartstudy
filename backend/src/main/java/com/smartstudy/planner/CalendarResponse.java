package com.smartstudy.planner;

import java.time.LocalDate;
import java.util.List;

/** GET /api/v1/planner/weekly and /planner/calendar - one entry per local day in the range. */
public record CalendarResponse(
        LocalDate from,
        LocalDate to,
        String timeZone,
        int totalPlannedMinutes,
        int totalCompletedMinutes,
        List<DayPlanResponse> days) {
}
