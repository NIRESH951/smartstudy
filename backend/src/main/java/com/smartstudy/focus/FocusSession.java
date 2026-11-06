package com.smartstudy.focus;

import java.time.Instant;

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
import com.smartstudy.subject.Subject;
import com.smartstudy.topic.Topic;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * A Pomodoro focus block. All timing fields are set from the server clock; the browser
 * countdown is only a display, so closing the tab or changing the computer clock cannot
 * inflate the recorded focus time.
 */
@Entity
@Table(name = "focus_sessions")
public class FocusSession extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_session_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private StudySession studySession;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FocusStatus status = FocusStatus.RUNNING;

    @Column(name = "planned_minutes", nullable = false)
    private int plannedMinutes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "total_paused_seconds", nullable = false)
    private int totalPausedSeconds;

    @Column(name = "pause_count", nullable = false)
    private int pauseCount;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "actual_focus_seconds")
    private Integer actualFocusSeconds;

    @Column(name = "client_reported_seconds")
    private Integer clientReportedSeconds;

    @Column(name = "notes", length = 500)
    private String notes;

    protected FocusSession() {
    }

    public FocusSession(Long userId, int plannedMinutes, Instant startedAt) {
        this.userId = userId;
        this.plannedMinutes = plannedMinutes;
        this.startedAt = startedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public StudySession getStudySession() {
        return studySession;
    }

    public void setStudySession(StudySession studySession) {
        this.studySession = studySession;
    }

    public FocusStatus getStatus() {
        return status;
    }

    public void setStatus(FocusStatus status) {
        this.status = status;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public void setPausedAt(Instant pausedAt) {
        this.pausedAt = pausedAt;
    }

    public int getTotalPausedSeconds() {
        return totalPausedSeconds;
    }

    public void setTotalPausedSeconds(int totalPausedSeconds) {
        this.totalPausedSeconds = totalPausedSeconds;
    }

    public int getPauseCount() {
        return pauseCount;
    }

    public void setPauseCount(int pauseCount) {
        this.pauseCount = pauseCount;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Integer getActualFocusSeconds() {
        return actualFocusSeconds;
    }

    public void setActualFocusSeconds(Integer actualFocusSeconds) {
        this.actualFocusSeconds = actualFocusSeconds;
    }

    public Integer getClientReportedSeconds() {
        return clientReportedSeconds;
    }

    public void setClientReportedSeconds(Integer clientReportedSeconds) {
        this.clientReportedSeconds = clientReportedSeconds;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
