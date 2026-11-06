package com.smartstudy.planner;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Manual session. Date and start time are in the student's local time zone.
 *
 * <pre>
 * POST /api/v1/sessions
 * { "subjectId": 3, "topicId": 12, "sessionType": "STUDY", "date": "2026-11-02",
 *   "startTime": "07:00", "durationMinutes": 45, "notes": "Before college", "locked": true }
 * </pre>
 * Either {@code topicId} or {@code subjectId} is required; the subject is derived from the topic.
 */
public record CreateSessionRequest(
        Long subjectId,
        Long topicId,
        SessionType sessionType,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @Min(15) @Max(480) int durationMinutes,
        @Size(max = 1000) String notes,
        Boolean locked) {
}
