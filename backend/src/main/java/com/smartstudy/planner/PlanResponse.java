package com.smartstudy.planner;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of POST /api/v1/planner/generate and /regenerate.
 *
 * <pre>
 * { "planId": 5, "startDate": "2026-10-31", "endDate": "2026-11-06", "generatedAt": "2026-10-31T06:00:00Z",
 *   "totalPlannedMinutes": 840, "sessionCount": 16,
 *   "strategySummary": "16 sessions (14 h 0 min) for 9 topics. Focus: AVL Trees, Normalization, Deadlocks.",
 *   "sessions": [ ...SessionResponse... ],
 *   "unscheduled": [ { "topicId": 20, "topicName": "Graph Coloring", "subjectName": "Discrete Maths",
 *                      "sessionType": "STUDY", "remainingMinutes": 90,
 *                      "reason": "Did not fit into this planning period; ..." } ],
 *   "warnings": [] }
 * </pre>
 */
public record PlanResponse(
        Long planId,
        LocalDate startDate,
        LocalDate endDate,
        Instant generatedAt,
        int totalPlannedMinutes,
        int sessionCount,
        String strategySummary,
        List<SessionResponse> sessions,
        List<UnscheduledItem> unscheduled,
        List<String> warnings) {

    public record UnscheduledItem(Long topicId, String topicName, String subjectName, SessionType sessionType,
                                  int remainingMinutes, String reason) {
    }
}
