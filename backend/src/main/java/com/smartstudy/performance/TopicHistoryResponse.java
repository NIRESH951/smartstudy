package com.smartstudy.performance;

import java.time.LocalDate;
import java.util.List;

import com.smartstudy.assessment.AssessmentType;

/**
 * GET /api/v1/performance/topics/{topicId}/history - every score of a topic and the mastery
 * value after each assessment, which shows the student's improvement over time.
 */
public record TopicHistoryResponse(
        Long topicId,
        String topicName,
        String subjectName,
        Double currentMastery,
        MasteryLevel masteryLevel,
        List<Point> points) {

    public record Point(Long assessmentId, String title, AssessmentType assessmentType, LocalDate assessedOn,
                        double scorePercent, double masteryAfter) {
    }
}
