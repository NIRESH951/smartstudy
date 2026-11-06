package com.smartstudy.planner;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Moves or edits a planned session (local date/time). Editing locks the session so the
 * planner never moves it back.
 *
 * <pre>
 * PUT /api/v1/sessions/41
 * { "date": "2026-11-03", "startTime": "19:00", "durationMinutes": 60, "notes": "Moved" }
 * </pre>
 */
public record UpdateSessionRequest(
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @Min(15) @Max(480) int durationMinutes,
        @Size(max = 1000) String notes) {
}
