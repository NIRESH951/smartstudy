package com.smartstudy.performance;

import java.util.List;
import java.util.Map;

/** GET /api/v1/performance/summary - mastery distribution and subject-level performance. */
public record PerformanceSummaryResponse(
        Double overallMastery,
        Map<String, Integer> levelCounts,
        List<SubjectPerformance> subjects) {

    public record SubjectPerformance(
            Long subjectId,
            String subjectName,
            String subjectColor,
            Double averageMastery,
            int assessedTopics,
            int totalTopics,
            int weakTopics,
            Double averageAssessmentScore,
            int assessmentCount) {
    }
}
