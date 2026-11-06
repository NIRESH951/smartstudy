package com.smartstudy.focus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * <pre>
 * POST /api/v1/focus/start
 * { "subjectId": 3, "topicId": 12, "studySessionId": 41, "plannedMinutes": 25 }
 * </pre>
 * All fields are optional; plannedMinutes defaults to the student's Pomodoro focus length.
 */
public record FocusStartRequest(
        Long subjectId,
        Long topicId,
        Long studySessionId,
        @Min(1) @Max(180) Integer plannedMinutes) {
}
