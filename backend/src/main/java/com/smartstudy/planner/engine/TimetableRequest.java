package com.smartstudy.planner.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Everything the timetable generator needs. It is a pure value object, so the algorithm can be
 * unit-tested without a database.
 *
 * @param startDate          first local date of the plan (inclusive)
 * @param endDate            last local date of the plan (inclusive)
 * @param zone               student's time zone
 * @param now                current instant; no session is placed before it
 * @param windows            weekly availability
 * @param busy               time already taken by preserved sessions (completed, in progress, locked)
 * @param usedMinutesByDay   minutes already used by preserved sessions per local date
 * @param usedMinutesByWeek  minutes already used per week, keyed by the Monday of the week
 * @param dailyCapMinutes    maximum study minutes per day
 * @param weeklyCapMinutes   maximum study minutes per Monday-Sunday week
 * @param sessionMinutes     preferred length of one session
 * @param breakMinutes       gap between consecutive sessions
 * @param tasks              work to schedule
 */
public record TimetableRequest(
        LocalDate startDate,
        LocalDate endDate,
        ZoneId zone,
        Instant now,
        List<AvailabilityWindow> windows,
        List<TimeInterval> busy,
        Map<LocalDate, Integer> usedMinutesByDay,
        Map<LocalDate, Integer> usedMinutesByWeek,
        int dailyCapMinutes,
        int weeklyCapMinutes,
        int sessionMinutes,
        int breakMinutes,
        List<PlanningTask> tasks) {
}
