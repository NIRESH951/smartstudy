package com.smartstudy.analytics;

import java.time.LocalDate;

/**
 * Study activity of one local day. {@code actualMinutes} = minutes of completed/incomplete study
 * sessions + focus sessions and revisions that were not part of a study session.
 */
public record DailyPoint(
        LocalDate date,
        String label,
        int plannedMinutes,
        int actualMinutes,
        int focusMinutes,
        int completedSessions,
        int missedSessions) {
}
