package com.smartstudy.user;

import java.time.Instant;

/**
 * The signed-in student's profile and preferences.
 *
 * <pre>
 * {
 *   "id": 1, "fullName": "Demo Student", "email": "demo@smartstudy.dev", "role": "STUDENT",
 *   "timeZone": "Asia/Kolkata", "dailyStudyMinutes": 180, "weeklyStudyMinutes": 900,
 *   "preferredSessionMinutes": 50, "breakMinutes": 10, "focusMinutes": 25,
 *   "shortBreakMinutes": 5, "longBreakMinutes": 15, "longBreakInterval": 4,
 *   "createdAt": "2026-10-01T10:00:00Z"
 * }
 * </pre>
 */
public record ProfileResponse(
        Long id,
        String fullName,
        String email,
        String role,
        String timeZone,
        int dailyStudyMinutes,
        int weeklyStudyMinutes,
        int preferredSessionMinutes,
        int breakMinutes,
        int focusMinutes,
        int shortBreakMinutes,
        int longBreakMinutes,
        int longBreakInterval,
        Instant createdAt) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole().name(),
                user.getTimeZone(), user.getDailyStudyMinutes(), user.getWeeklyStudyMinutes(),
                user.getPreferredSessionMinutes(), user.getBreakMinutes(), user.getFocusMinutes(),
                user.getShortBreakMinutes(), user.getLongBreakMinutes(), user.getLongBreakInterval(),
                user.getCreatedAt());
    }
}
