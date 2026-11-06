package com.smartstudy.focus;

import java.time.LocalDate;
import java.util.List;

/** GET /api/v1/focus/stats */
public record FocusStatsResponse(
        int todayFocusMinutes,
        int todayCompletedSessions,
        int weekFocusMinutes,
        int last30DaysSessions,
        int last30DaysFocusMinutes,
        double averageSessionMinutes,
        int completedInCurrentCycle,
        int longBreakInterval,
        String nextBreak,
        int focusMinutes,
        int shortBreakMinutes,
        int longBreakMinutes,
        List<DailyFocus> daily,
        List<SubjectFocus> bySubject) {

    public record DailyFocus(LocalDate date, int minutes, int sessions) {
    }

    public record SubjectFocus(Long subjectId, String subjectName, String subjectColor, int minutes) {
    }
}
