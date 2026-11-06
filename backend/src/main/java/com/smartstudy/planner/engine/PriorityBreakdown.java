package com.smartstudy.planner.engine;

import java.util.List;

/**
 * Result of the priority formula: every normalised factor (0-100), the weighted priority
 * (0-100) and human-readable reasons ordered by how much each factor contributed.
 */
public record PriorityBreakdown(
        double weakness,
        double examUrgency,
        double difficulty,
        double revisionOverdue,
        double subjectImportance,
        double priority,
        List<String> reasons) {

    /** Reasons joined into one sentence, short enough for the study_sessions.reason column. */
    public String summary() {
        String joined = reasons.isEmpty() ? "Balanced syllabus coverage" : String.join("; ", reasons);
        return joined.length() <= 500 ? joined : joined.substring(0, 497) + "...";
    }
}
