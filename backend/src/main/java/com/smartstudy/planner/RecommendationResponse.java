package com.smartstudy.planner;

import java.time.LocalDate;
import java.util.List;

/**
 * "What to study next and why".
 *
 * <pre>
 * { "topicId": 12, "topicName": "AVL Trees", "subjectId": 3, "subjectName": "Data Structures",
 *   "subjectColor": "#3B82F6", "sessionType": "STUDY", "priority": 75.0,
 *   "factors": { "weakness": 70.0, "examUrgency": 80.0, "difficulty": 75.0,
 *                "revisionOverdue": 60.0, "subjectImportance": 100.0 },
 *   "reasons": ["Weak topic (mastery 30%)", "Exam in 6 days", "Hard topic (difficulty 4/5)"],
 *   "masteryScore": 30.0, "masteryLevel": "WEAK", "daysUntilExam": 6,
 *   "nextRevisionDate": "2026-10-24", "remainingMinutes": 120 }
 * </pre>
 */
public record RecommendationResponse(
        Long topicId,
        String topicName,
        Long subjectId,
        String subjectName,
        String subjectColor,
        SessionType sessionType,
        double priority,
        Factors factors,
        List<String> reasons,
        Double masteryScore,
        String masteryLevel,
        Integer daysUntilExam,
        LocalDate nextRevisionDate,
        int remainingMinutes) {

    public record Factors(double weakness, double examUrgency, double difficulty, double revisionOverdue,
                          double subjectImportance) {
    }
}
