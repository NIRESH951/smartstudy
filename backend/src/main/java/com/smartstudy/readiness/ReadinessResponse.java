package com.smartstudy.readiness;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/exams/{id}/readiness
 *
 * <pre>
 * { "examId": 7, "examTitle": "End Semester Exam", "subjectId": 3, "subjectName": "Data Structures",
 *   "subjectColor": "#3B82F6", "examDate": "2026-11-20", "daysRemaining": 20,
 *   "readinessScore": 63.4, "level": "ON_TRACK", "dataConfidence": 100.0, "dataSufficiency": "SUFFICIENT",
 *   "components": [
 *     { "key": "syllabusCompletion", "label": "Syllabus completion", "score": 50.0, "weight": 0.30,
 *       "available": true, "detail": "3 of 6 topics completed" }, ... ],
 *   "pendingTopics": [ { "topicId": 12, "name": "AVL Trees", "status": "IN_PROGRESS", "difficulty": 4,
 *                        "masteryScore": 32.4 } ],
 *   "focusAreas": [ { "type": "WEAK_TOPIC", "title": "AVL Trees", "detail": "Mastery 32% (weak)", "topicId": 12 } ],
 *   "history": [ { "date": "2026-10-30", "score": 61.0 }, { "date": "2026-10-31", "score": 63.4 } ],
 *   "calculatedOn": "2026-10-31",
 *   "disclaimer": "This is an estimate of your preparation, not a prediction of your actual exam result." }
 * </pre>
 */
public record ReadinessResponse(
        Long examId,
        String examTitle,
        Long subjectId,
        String subjectName,
        String subjectColor,
        LocalDate examDate,
        int daysRemaining,
        Double readinessScore,
        ReadinessLevel level,
        double dataConfidence,
        DataSufficiency dataSufficiency,
        List<Component> components,
        List<PendingTopic> pendingTopics,
        List<FocusArea> focusAreas,
        List<TrendPoint> history,
        LocalDate calculatedOn,
        String disclaimer) {

    public record Component(String key, String label, Double score, double weight, boolean available,
                            String detail) {
    }

    public record PendingTopic(Long topicId, String name, String status, int difficulty, Double masteryScore) {
    }

    public record FocusArea(String type, String title, String detail, Long topicId) {
    }

    public record TrendPoint(LocalDate date, Double score) {
    }

    /** One row of GET /api/v1/readiness (all upcoming exams). */
    public record OverviewItem(Long examId, String examTitle, Long subjectId, String subjectName,
                               String subjectColor, LocalDate examDate, int daysRemaining, Double readinessScore,
                               ReadinessLevel level, DataSufficiency dataSufficiency) {
    }
}
