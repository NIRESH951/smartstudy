package com.smartstudy.user;

import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Replaces the student's study-time settings and weekly availability.
 *
 * <pre>
 * PUT /api/v1/availability
 * {
 *   "dailyStudyMinutes": 180, "weeklyStudyMinutes": 900,
 *   "preferredSessionMinutes": 50, "breakMinutes": 10,
 *   "windows": [
 *     { "dayOfWeek": 1, "startTime": "18:00", "endTime": "21:00" },
 *     { "dayOfWeek": 6, "startTime": "09:00", "endTime": "12:30" }
 *   ]
 * }
 * </pre>
 */
public record AvailabilityRequest(
        @Min(0) @Max(1440) int dailyStudyMinutes,
        @Min(0) @Max(10080) int weeklyStudyMinutes,
        @Min(15) @Max(240) int preferredSessionMinutes,
        @Min(0) @Max(60) int breakMinutes,
        @NotNull @Size(max = 42) List<@Valid Window> windows) {

    public record Window(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime) {
    }
}
