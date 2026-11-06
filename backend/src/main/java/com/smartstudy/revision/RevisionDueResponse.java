package com.smartstudy.revision;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/revisions/due
 *
 * <pre>
 * { "today": "2026-10-31", "dueCount": 3, "overdueCount": 1, "upcomingCount": 4, "completedThisWeek": 6,
 *   "overdue": [ ... ], "dueToday": [ ... ], "upcoming": [ ...next 7 days... ] }
 * </pre>
 */
public record RevisionDueResponse(
        LocalDate today,
        int dueCount,
        int overdueCount,
        int upcomingCount,
        int completedThisWeek,
        List<RevisionItemResponse> overdue,
        List<RevisionItemResponse> dueToday,
        List<RevisionItemResponse> upcoming) {
}
