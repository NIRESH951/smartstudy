package com.smartstudy.planner;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.smartstudy.common.BaseEntity;

/** Metadata of one generated timetable. Only one plan per student is ACTIVE at a time. */
@Entity
@Table(name = "study_plans")
public class StudyPlan extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanStatus status = PlanStatus.ACTIVE;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "total_planned_minutes", nullable = false)
    private int totalPlannedMinutes;

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @Column(name = "strategy_summary", length = 500)
    private String strategySummary;

    protected StudyPlan() {
    }

    public StudyPlan(Long userId, LocalDate startDate, LocalDate endDate, Instant generatedAt) {
        this.userId = userId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.generatedAt = generatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public int getTotalPlannedMinutes() {
        return totalPlannedMinutes;
    }

    public void setTotalPlannedMinutes(int totalPlannedMinutes) {
        this.totalPlannedMinutes = totalPlannedMinutes;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }

    public String getStrategySummary() {
        return strategySummary;
    }

    public void setStrategySummary(String strategySummary) {
        this.strategySummary = strategySummary;
    }
}
