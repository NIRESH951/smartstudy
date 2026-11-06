package com.smartstudy.planner.engine;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.smartstudy.planner.SessionType;

/** Output of the timetable generator. */
public record TimetableResult(List<PlannedSlot> slots, List<UnscheduledWork> unscheduled) {

    public int totalMinutes() {
        return slots.stream().mapToInt(PlannedSlot::minutes).sum();
    }

    /** One generated session. */
    public record PlannedSlot(PlanningTask task, LocalDate date, Instant start, Instant end) {

        public int minutes() {
            return (int) Duration.between(start, end).toMinutes();
        }
    }

    /** Work that did not fit into the available time, with an explanation. */
    public record UnscheduledWork(long topicId, long subjectId, SessionType sessionType, int remainingMinutes,
                                  String reason) {
    }
}
