package com.smartstudy.analytics;

import java.time.LocalDate;

/** One row of GET /api/v1/analytics/subjects - subject-wise progress. */
public record SubjectAnalyticsResponse(
        Long subjectId,
        String subjectName,
        String subjectColor,
        int totalTopics,
        int completedTopics,
        double syllabusCompletion,
        Double averageMastery,
        int weakTopics,
        int studiedMinutesTotal,
        int studiedMinutesLast30Days,
        int plannedMinutesNext7Days,
        Double assessmentAverage,
        LocalDate lastStudiedOn) {
}
