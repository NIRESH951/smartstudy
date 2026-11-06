package com.smartstudy.revision;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.smartstudy.common.BaseEntity;
import com.smartstudy.planner.StudySession;
import com.smartstudy.topic.Topic;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Immutable record of one spaced-repetition review: rating plus SM-2 state before and after. */
@Entity
@Table(name = "revision_logs")
public class RevisionLog extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_session_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private StudySession studySession;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 10)
    private RevisionRating rating;

    @Column(name = "revised_on", nullable = false)
    private LocalDate revisedOn;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "revised_at", nullable = false)
    private Instant revisedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "previous_ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal previousEaseFactor;

    @Column(name = "new_ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal newEaseFactor;

    @Column(name = "previous_interval_days", nullable = false)
    private int previousIntervalDays;

    @Column(name = "new_interval_days", nullable = false)
    private int newIntervalDays;

    @Column(name = "repetitions", nullable = false)
    private int repetitions;

    @Column(name = "next_revision_date", nullable = false)
    private LocalDate nextRevisionDate;

    @Column(name = "notes", length = 500)
    private String notes;

    protected RevisionLog() {
    }

    public RevisionLog(Long userId, Topic topic, RevisionRating rating, LocalDate revisedOn, LocalDate dueDate,
                       Instant revisedAt, BigDecimal previousEaseFactor, BigDecimal newEaseFactor,
                       int previousIntervalDays, int newIntervalDays, int repetitions, LocalDate nextRevisionDate) {
        this.userId = userId;
        this.topic = topic;
        this.rating = rating;
        this.revisedOn = revisedOn;
        this.dueDate = dueDate;
        this.revisedAt = revisedAt;
        this.previousEaseFactor = previousEaseFactor;
        this.newEaseFactor = newEaseFactor;
        this.previousIntervalDays = previousIntervalDays;
        this.newIntervalDays = newIntervalDays;
        this.repetitions = repetitions;
        this.nextRevisionDate = nextRevisionDate;
    }

    /** True when the review happened on or before its due date. */
    public boolean isOnTime() {
        return dueDate == null || !revisedOn.isAfter(dueDate);
    }

    public Long getUserId() {
        return userId;
    }

    public Topic getTopic() {
        return topic;
    }

    public StudySession getStudySession() {
        return studySession;
    }

    public void setStudySession(StudySession studySession) {
        this.studySession = studySession;
    }

    public RevisionRating getRating() {
        return rating;
    }

    public LocalDate getRevisedOn() {
        return revisedOn;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getRevisedAt() {
        return revisedAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public BigDecimal getPreviousEaseFactor() {
        return previousEaseFactor;
    }

    public BigDecimal getNewEaseFactor() {
        return newEaseFactor;
    }

    public int getPreviousIntervalDays() {
        return previousIntervalDays;
    }

    public int getNewIntervalDays() {
        return newIntervalDays;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public LocalDate getNextRevisionDate() {
        return nextRevisionDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
