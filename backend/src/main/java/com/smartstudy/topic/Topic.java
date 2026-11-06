package com.smartstudy.topic;

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
import com.smartstudy.subject.Subject;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * A syllabus topic of a subject. Besides its syllabus status it stores the topic's
 * spaced-repetition (SM-2) state: ease factor, interval, repetition count and next due date.
 */
@Entity
@Table(name = "topics")
public class Topic extends BaseEntity {

    public static final BigDecimal DEFAULT_EASE_FACTOR = new BigDecimal("2.50");

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Subject subject;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    /** 1 (very easy) .. 5 (very hard). */
    @Column(name = "difficulty", nullable = false)
    private int difficulty = 3;

    /** Estimated total study time needed to cover the topic once. */
    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes = 120;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TopicStatus status = TopicStatus.NOT_STARTED;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "revision_ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal revisionEaseFactor = DEFAULT_EASE_FACTOR;

    @Column(name = "revision_interval_days", nullable = false)
    private int revisionIntervalDays;

    @Column(name = "revision_repetitions", nullable = false)
    private int revisionRepetitions;

    /** Null while the topic has not entered the revision cycle. */
    @Column(name = "next_revision_date")
    private LocalDate nextRevisionDate;

    @Column(name = "last_revised_on")
    private LocalDate lastRevisedOn;

    protected Topic() {
    }

    public Topic(Long userId, Subject subject, String name) {
        this.userId = userId;
        this.subject = subject;
        this.name = name;
    }

    public Long getUserId() {
        return userId;
    }

    public Subject getSubject() {
        return subject;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(int estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public TopicStatus getStatus() {
        return status;
    }

    public void setStatus(TopicStatus status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public BigDecimal getRevisionEaseFactor() {
        return revisionEaseFactor;
    }

    public void setRevisionEaseFactor(BigDecimal revisionEaseFactor) {
        this.revisionEaseFactor = revisionEaseFactor;
    }

    public int getRevisionIntervalDays() {
        return revisionIntervalDays;
    }

    public void setRevisionIntervalDays(int revisionIntervalDays) {
        this.revisionIntervalDays = revisionIntervalDays;
    }

    public int getRevisionRepetitions() {
        return revisionRepetitions;
    }

    public void setRevisionRepetitions(int revisionRepetitions) {
        this.revisionRepetitions = revisionRepetitions;
    }

    public LocalDate getNextRevisionDate() {
        return nextRevisionDate;
    }

    public void setNextRevisionDate(LocalDate nextRevisionDate) {
        this.nextRevisionDate = nextRevisionDate;
    }

    public LocalDate getLastRevisedOn() {
        return lastRevisedOn;
    }

    public void setLastRevisedOn(LocalDate lastRevisedOn) {
        this.lastRevisedOn = lastRevisedOn;
    }
}
