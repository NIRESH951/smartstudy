package com.smartstudy.analytics;

import java.time.LocalDate;
import java.util.List;

/** GET /api/v1/analytics/productivity?days=30 - long-term trends for the analytics page. */
public record ProductivityResponse(
        int days,
        int totalPlannedMinutes,
        int totalActualMinutes,
        double plannedVsActualPercent,
        double consistencyPercent,
        int activeDays,
        int currentStreak,
        int longestStreak,
        SessionStats sessions,
        RevisionStats revisions,
        FocusStats focus,
        List<DailyPoint> daily,
        List<MonthlyPoint> monthly,
        List<WeakSubject> weakSubjects,
        List<ReadinessTrend> readinessTrends) {

    public record SessionStats(int completed, int incomplete, int missed, double completionRate) {
    }

    public record RevisionStats(int completed, int onTime, double onTimeRate, long overdueNow) {
    }

    public record FocusStats(int sessions, int totalMinutes, double averageMinutes) {
    }

    public record MonthlyPoint(String month, int actualMinutes, int focusMinutes) {
    }

    public record WeakSubject(Long subjectId, String subjectName, String subjectColor, Double averageMastery,
                              int weakTopics, int assessedTopics, int totalTopics) {
    }

    public record ReadinessTrend(Long examId, String examTitle, String subjectName, String subjectColor,
                                 LocalDate examDate, List<TrendPoint> points) {
    }

    public record TrendPoint(LocalDate date, Double score) {
    }
}
