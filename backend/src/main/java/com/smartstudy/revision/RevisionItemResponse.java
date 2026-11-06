package com.smartstudy.revision;

import java.time.LocalDate;

/**
 * A topic in the revision queue. {@code daysOverdue} is positive when overdue, 0 when due today
 * and negative for upcoming revisions (e.g. -3 = due in three days).
 */
public record RevisionItemResponse(
        Long topicId,
        String topicName,
        Long subjectId,
        String subjectName,
        String subjectColor,
        LocalDate nextRevisionDate,
        int daysOverdue,
        String dueStatus,
        int intervalDays,
        int repetitions,
        double easeFactor,
        LocalDate lastRevisedOn,
        Double masteryScore,
        String masteryLevel) {
}
