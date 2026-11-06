package com.smartstudy.planner;

/**
 * Lightweight reference to a missed or incomplete session whose remaining work is being
 * rescheduled. It survives the persistence-context clear performed by bulk deletes.
 */
public record SessionRef(Long sessionId, Long topicId, SessionType sessionType) {

    public static SessionRef of(StudySession session) {
        return new SessionRef(session.getId(), session.getTopic() == null ? null : session.getTopic().getId(),
                session.getSessionType());
    }
}
