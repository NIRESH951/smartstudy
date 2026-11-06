package com.smartstudy.subject;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Subject with progress statistics.
 *
 * <pre>
 * { "id": 3, "name": "Data Structures", "code": "CS201", "color": "#3B82F6", "importance": 5,
 *   "targetScore": 85.0, "description": "...", "archived": false,
 *   "topicCount": 6, "completedTopicCount": 3, "syllabusCompletion": 50.0,
 *   "averageMastery": 61.2, "assessedTopicCount": 4, "weakTopicCount": 1,
 *   "nextExamId": 7, "nextExamTitle": "End semester", "nextExamDate": "2026-11-20", "daysUntilNextExam": 20,
 *   "createdAt": "2026-10-01T10:00:00Z" }
 * </pre>
 */
public record SubjectResponse(
        Long id,
        String name,
        String code,
        String color,
        int importance,
        Double targetScore,
        String description,
        boolean archived,
        int topicCount,
        int completedTopicCount,
        double syllabusCompletion,
        Double averageMastery,
        int assessedTopicCount,
        int weakTopicCount,
        Long nextExamId,
        String nextExamTitle,
        LocalDate nextExamDate,
        Integer daysUntilNextExam,
        Instant createdAt) {
}
