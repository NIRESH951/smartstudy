package com.smartstudy.performance;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/performance/weak-topics
 *
 * <pre>
 * { "totalTopics": 24, "assessedTopics": 15, "notAssessedTopics": 9,
 *   "items": [ { "topicId": 12, "topicName": "AVL Trees", "subjectId": 3, "subjectName": "Data Structures",
 *                "subjectColor": "#3B82F6", "masteryScore": 32.4, "masteryLevel": "WEAK",
 *                "latestScore": 30.0, "historicalAverage": 36.0, "previousMasteryScore": 41.0,
 *                "trend": "DECLINING", "assessmentCount": 2, "lastAssessedOn": "2026-10-28",
 *                "topicStatus": "IN_PROGRESS",
 *                "recommendation": "Re-study the fundamentals ..." } ] }
 * </pre>
 */
public record WeakTopicResponse(int totalTopics, int assessedTopics, int notAssessedTopics, List<Item> items) {

    public record Item(
            Long topicId,
            String topicName,
            Long subjectId,
            String subjectName,
            String subjectColor,
            double masteryScore,
            MasteryLevel masteryLevel,
            double latestScore,
            double historicalAverage,
            Double previousMasteryScore,
            String trend,
            int assessmentCount,
            LocalDate lastAssessedOn,
            String topicStatus,
            String recommendation) {
    }
}
