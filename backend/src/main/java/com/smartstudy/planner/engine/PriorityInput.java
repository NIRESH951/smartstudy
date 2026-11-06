package com.smartstudy.planner.engine;

/**
 * Raw facts about a topic that feed the priority formula.
 *
 * @param masteryScore      0-100, or null when the topic has never been assessed
 * @param daysUntilExam     days from today until the nearest upcoming exam of the subject (0 = today),
 *                          or null when the subject has no upcoming exam
 * @param difficulty        topic difficulty 1-5
 * @param daysOverdue       null when no revision is due; 0 = due today; n = overdue by n days
 * @param subjectImportance subject importance 1-5
 */
public record PriorityInput(
        Double masteryScore,
        Integer daysUntilExam,
        int difficulty,
        Integer daysOverdue,
        int subjectImportance) {
}
