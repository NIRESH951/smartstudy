package com.smartstudy.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT /api/v1/users/me - profile and Pomodoro settings. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 64) String timeZone,
        @Min(5) @Max(120) int focusMinutes,
        @Min(1) @Max(60) int shortBreakMinutes,
        @Min(1) @Max(120) int longBreakMinutes,
        @Min(2) @Max(12) int longBreakInterval) {
}
