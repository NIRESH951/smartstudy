package com.smartstudy.analytics;

import java.time.LocalDate;
import java.util.List;

/** GET /api/v1/analytics/weekly?date=2026-10-31 - Monday-Sunday week containing the date. */
public record WeeklyAnalyticsResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        int plannedMinutes,
        int actualMinutes,
        int focusMinutes,
        int completedSessions,
        int missedSessions,
        double completionRate,
        List<DailyPoint> days,
        List<SubjectMinutes> bySubject) {

    public record SubjectMinutes(Long subjectId, String subjectName, String subjectColor, int plannedMinutes,
                                 int actualMinutes) {
    }
}
