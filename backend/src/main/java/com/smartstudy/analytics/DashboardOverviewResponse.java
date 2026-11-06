package com.smartstudy.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.smartstudy.readiness.DataSufficiency;
import com.smartstudy.readiness.ReadinessLevel;

/**
 * GET /api/v1/analytics/overview - everything the main dashboard shows.
 *
 * <pre>
 * { "date": "2026-10-31", "timeZone": "Asia/Kolkata",
 *   "today": { "plannedMinutes": 150, "completedMinutes": 50, "completionPercent": 33.33,
 *              "sessionsPlanned": 3, "sessionsCompleted": 1, "sessionsMissed": 0 },
 *   "week": { "plannedMinutes": 840, "actualMinutes": 410, "focusMinutes": 175,
 *             "completedSessions": 8, "missedSessions": 1 },
 *   "streakDays": 5, "consistencyPercent": 78.57,
 *   "upcomingExams": [ ... ], "weakTopics": [ ... ],
 *   "revisions": { "dueCount": 3, "overdueCount": 1, "items": [ ... ] },
 *   "weeklyProductivity": [ { "date": "2026-10-25", "label": "Sun", "plannedMinutes": 120, ... } ],
 *   "recentActivity": [ { "type": "SESSION_COMPLETED", "title": "AVL Trees", "detail": "...",
 *                         "occurredAt": "2026-10-31T05:10:00Z" } ],
 *   "unreadNotifications": 2 }
 * </pre>
 */
public record DashboardOverviewResponse(
        LocalDate date,
        String timeZone,
        TodaySummary today,
        WeekSummary week,
        int streakDays,
        double consistencyPercent,
        List<UpcomingExam> upcomingExams,
        List<WeakTopic> weakTopics,
        RevisionSummary revisions,
        List<DailyPoint> weeklyProductivity,
        List<ActivityItem> recentActivity,
        long unreadNotifications) {

    public record TodaySummary(int plannedMinutes, int completedMinutes, double completionPercent,
                               int sessionsPlanned, int sessionsCompleted, int sessionsMissed) {
    }

    public record WeekSummary(int plannedMinutes, int actualMinutes, int focusMinutes, int completedSessions,
                              int missedSessions) {
    }

    public record UpcomingExam(Long examId, String title, String subjectName, String subjectColor,
                               LocalDate examDate, int daysRemaining, Double readinessScore,
                               ReadinessLevel readinessLevel, DataSufficiency dataSufficiency) {
    }

    public record WeakTopic(Long topicId, String topicName, String subjectName, String subjectColor,
                            double masteryScore, String masteryLevel) {
    }

    public record RevisionSummary(int dueCount, int overdueCount, List<RevisionItem> items) {
    }

    public record RevisionItem(Long topicId, String topicName, String subjectName, LocalDate nextRevisionDate,
                               int daysOverdue) {
    }

    public record ActivityItem(String type, String title, String detail, Instant occurredAt) {
    }
}
