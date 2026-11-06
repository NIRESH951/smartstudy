package com.smartstudy.planner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.smartstudy.common.ScoreMath;

/**
 * A study session. Instants are UTC; {@code localDate}, {@code localStartTime} and
 * {@code localEndTime} are pre-computed in the student's time zone for display.
 *
 * <pre>
 * { "id": 41, "planId": 5, "subjectId": 3, "subjectName": "Data Structures", "subjectColor": "#3B82F6",
 *   "topicId": 12, "topicName": "AVL Trees", "sessionType": "STUDY", "source": "GENERATED",
 *   "status": "PLANNED", "startAt": "2026-11-02T12:30:00Z", "endAt": "2026-11-02T13:20:00Z",
 *   "localDate": "2026-11-02", "localStartTime": "18:00:00", "localEndTime": "18:50:00",
 *   "plannedMinutes": 50, "actualMinutes": null, "startedAt": null, "completedAt": null,
 *   "locked": false, "priorityScore": 72.5,
 *   "reason": "Weak topic (mastery 32%); Exam in 6 days; Hard topic (difficulty 4/5)",
 *   "notes": null, "rescheduledFromId": null }
 * </pre>
 */
public record SessionResponse(
        Long id,
        Long planId,
        Long subjectId,
        String subjectName,
        String subjectColor,
        Long topicId,
        String topicName,
        SessionType sessionType,
        SessionSource source,
        SessionStatus status,
        Instant startAt,
        Instant endAt,
        LocalDate localDate,
        LocalTime localStartTime,
        LocalTime localEndTime,
        int plannedMinutes,
        Integer actualMinutes,
        Instant startedAt,
        Instant completedAt,
        boolean locked,
        Double priorityScore,
        String reason,
        String notes,
        Long rescheduledFromId) {

    public static SessionResponse from(StudySession session, ZoneId zone) {
        ZonedDateTime start = session.getStartAt().atZone(zone);
        ZonedDateTime end = session.getEndAt().atZone(zone);
        return new SessionResponse(
                session.getId(),
                session.getPlan() == null ? null : session.getPlan().getId(),
                session.getSubject().getId(),
                session.getSubject().getName(),
                session.getSubject().getColor(),
                session.getTopic() == null ? null : session.getTopic().getId(),
                session.getTopic() == null ? null : session.getTopic().getName(),
                session.getSessionType(),
                session.getSource(),
                session.getStatus(),
                session.getStartAt(),
                session.getEndAt(),
                start.toLocalDate(),
                start.toLocalTime(),
                end.toLocalTime(),
                session.getPlannedMinutes(),
                session.getActualMinutes(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.isLocked(),
                ScoreMath.toDouble(session.getPriorityScore()),
                session.getReason(),
                session.getNotes(),
                session.getRescheduledFrom() == null ? null : session.getRescheduledFrom().getId());
    }
}
