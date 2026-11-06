package com.smartstudy.focus;

import java.time.Instant;

/**
 * A focus session as seen by the client. {@code serverTime}, {@code elapsedFocusSeconds} and
 * {@code remainingSeconds} let the browser synchronise its countdown with the server clock.
 *
 * <pre>
 * { "id": 9, "status": "RUNNING", "subjectId": 3, "subjectName": "Data Structures", "subjectColor": "#3B82F6",
 *   "topicId": 12, "topicName": "AVL Trees", "studySessionId": null, "plannedMinutes": 25,
 *   "startedAt": "2026-10-31T06:00:00Z", "pausedAt": null, "totalPausedSeconds": 0, "pauseCount": 0,
 *   "endedAt": null, "actualFocusSeconds": null, "elapsedFocusSeconds": 312, "remainingSeconds": 1188,
 *   "serverTime": "2026-10-31T06:05:12Z" }
 * </pre>
 */
public record FocusSessionResponse(
        Long id,
        FocusStatus status,
        Long subjectId,
        String subjectName,
        String subjectColor,
        Long topicId,
        String topicName,
        Long studySessionId,
        int plannedMinutes,
        Instant startedAt,
        Instant pausedAt,
        int totalPausedSeconds,
        int pauseCount,
        Instant endedAt,
        Integer actualFocusSeconds,
        long elapsedFocusSeconds,
        long remainingSeconds,
        String notes,
        Instant serverTime) {
}
