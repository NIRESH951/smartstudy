package com.smartstudy.planner.engine;

import java.time.Instant;
import java.time.LocalDate;

import com.smartstudy.planner.SessionType;

/**
 * A unit of work the timetable generator has to place into free time.
 * {@code remainingMinutes} is decremented by the generator as sessions are assigned.
 */
public final class PlanningTask {

    private final long topicId;
    private final long subjectId;
    private final SessionType sessionType;
    private final LocalDate earliestDate;
    private final Instant deadline;
    private final double priority;
    private final String reason;
    private int remainingMinutes;

    /**
     * @param earliestDate first local date on which the task may be scheduled (e.g. revision due date)
     * @param deadline     sessions must end at or before this instant (exam start), or null for no deadline
     */
    public PlanningTask(long topicId, long subjectId, SessionType sessionType, int remainingMinutes,
                        LocalDate earliestDate, Instant deadline, double priority, String reason) {
        this.topicId = topicId;
        this.subjectId = subjectId;
        this.sessionType = sessionType;
        this.remainingMinutes = remainingMinutes;
        this.earliestDate = earliestDate;
        this.deadline = deadline;
        this.priority = priority;
        this.reason = reason;
    }

    public long topicId() {
        return topicId;
    }

    public long subjectId() {
        return subjectId;
    }

    public SessionType sessionType() {
        return sessionType;
    }

    public int remainingMinutes() {
        return remainingMinutes;
    }

    public LocalDate earliestDate() {
        return earliestDate;
    }

    public Instant deadline() {
        return deadline;
    }

    public double priority() {
        return priority;
    }

    public String reason() {
        return reason;
    }

    void consume(int minutes) {
        remainingMinutes = Math.max(0, remainingMinutes - minutes);
    }

    String key() {
        return topicId + ":" + sessionType;
    }
}
