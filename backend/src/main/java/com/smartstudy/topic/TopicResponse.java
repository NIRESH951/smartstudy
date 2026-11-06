package com.smartstudy.topic;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Topic with study progress, mastery and spaced-repetition state.
 *
 * <pre>
 * { "id": 12, "subjectId": 3, "subjectName": "Data Structures", "subjectColor": "#3B82F6",
 *   "name": "AVL Trees", "description": null, "difficulty": 4, "estimatedMinutes": 180,
 *   "studiedMinutes": 95, "status": "IN_PROGRESS", "completedAt": null, "sortOrder": 2,
 *   "masteryScore": 38.4, "masteryLevel": "WEAK", "assessmentCount": 2,
 *   "nextRevisionDate": null, "lastRevisedOn": null, "revisionIntervalDays": 0,
 *   "revisionRepetitions": 0, "easeFactor": 2.5, "createdAt": "2026-10-01T10:00:00Z" }
 * </pre>
 */
public record TopicResponse(
        Long id,
        Long subjectId,
        String subjectName,
        String subjectColor,
        String name,
        String description,
        int difficulty,
        int estimatedMinutes,
        int studiedMinutes,
        TopicStatus status,
        Instant completedAt,
        int sortOrder,
        Double masteryScore,
        String masteryLevel,
        int assessmentCount,
        LocalDate nextRevisionDate,
        LocalDate lastRevisedOn,
        int revisionIntervalDays,
        int revisionRepetitions,
        double easeFactor,
        Instant createdAt) {
}
