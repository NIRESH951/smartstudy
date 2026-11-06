package com.smartstudy.user;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.smartstudy.common.BaseEntity;

/**
 * A recurring weekly time window in which the student is available to study,
 * expressed in the student's local time. {@code dayOfWeek} follows ISO-8601 (1 = Monday).
 */
@Entity
@Table(name = "weekly_availability")
public class WeeklyAvailability extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected WeeklyAvailability() {
    }

    public WeeklyAvailability(Long userId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.userId = userId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getUserId() {
        return userId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
