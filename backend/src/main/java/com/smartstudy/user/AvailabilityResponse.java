package com.smartstudy.user;

import java.time.LocalTime;
import java.util.List;

public record AvailabilityResponse(
        int dailyStudyMinutes,
        int weeklyStudyMinutes,
        int preferredSessionMinutes,
        int breakMinutes,
        int totalWeeklyAvailableMinutes,
        List<WindowResponse> windows) {

    public record WindowResponse(Long id, int dayOfWeek, LocalTime startTime, LocalTime endTime, int minutes) {
    }
}
