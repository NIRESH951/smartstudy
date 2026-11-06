package com.smartstudy.planner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.smartstudy.common.TimeUtils;
import com.smartstudy.common.exception.BadRequestException;
import com.smartstudy.common.exception.BusinessRuleException;
import com.smartstudy.common.exception.ConflictException;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.focus.FocusSessionRepository;
import com.smartstudy.focus.FocusStatus;
import com.smartstudy.planner.engine.TimeInterval;
import com.smartstudy.revision.RevisionService;
import com.smartstudy.subject.Subject;
import com.smartstudy.subject.SubjectService;
import com.smartstudy.topic.Topic;
import com.smartstudy.topic.TopicService;
import com.smartstudy.topic.TopicStatus;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Study-session life cycle: manual creation with conflict detection, lock/unlock, start,
 * complete (with server-side duration validation), miss and automatic rescheduling.
 */
@Service
public class SessionService {

    /** A session counts as COMPLETED when at least this share of the planned time was studied. */
    static final double COMPLETION_THRESHOLD = 0.80;
    /** A session may be started up to this many minutes before its scheduled start. */
    static final int EARLY_START_MINUTES = 60;
    /** A missed session can still be logged as done within this many hours after its end. */
    static final int LATE_COMPLETION_HOURS = 48;
    static final int MANUAL_PAST_DAYS = 30;
    static final int MANUAL_FUTURE_DAYS = 90;

    private final StudySessionRepository sessionRepository;
    private final SubjectService subjectService;
    private final TopicService topicService;
    private final UserService userService;
    private final PlannerService plannerService;
    private final RevisionService revisionService;
    private final FocusSessionRepository focusRepository;
    private final Clock clock;

    public SessionService(StudySessionRepository sessionRepository, SubjectService subjectService,
                          TopicService topicService, UserService userService, PlannerService plannerService,
                          RevisionService revisionService, FocusSessionRepository focusRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.subjectService = subjectService;
        this.topicService = topicService;
        this.userService = userService;
        this.plannerService = plannerService;
        this.revisionService = revisionService;
        this.focusRepository = focusRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SessionResponse get(Long userId, Long sessionId) {
        return SessionResponse.from(require(userId, sessionId), zone(userId));
    }

    /** Creates a manual session after checking that it does not overlap any other session. */
    @Transactional
    public SessionResponse create(Long userId, CreateSessionRequest request) {
        ZoneId zone = zone(userId);
        LocalDate today = TimeUtils.today(clock, zone);
        validateDateWindow(request.date(), today);

        Topic topic = null;
        Subject subject;
        if (request.topicId() != null) {
            topic = topicService.require(userId, request.topicId());
            subject = topic.getSubject();
            if (request.subjectId() != null && !request.subjectId().equals(subject.getId())) {
                throw new BadRequestException("topicId", "The topic does not belong to the selected subject.");
            }
        } else if (request.subjectId() != null) {
            subject = subjectService.require(userId, request.subjectId());
        } else {
            throw new BadRequestException("subjectId", "Choose a subject or a topic for the session.");
        }

        Instant start = TimeUtils.toInstant(request.date(), request.startTime(), zone);
        Instant end = start.plusSeconds(request.durationMinutes() * 60L);
        ensureNoConflict(userId, start, end, null, zone);

        SessionType type = request.sessionType() == null ? SessionType.STUDY : request.sessionType();
        StudySession session = new StudySession(userId, subject, topic, type, SessionSource.MANUAL, start, end);
        session.setLocked(request.locked() == null || request.locked());
        session.setNotes(blankToNull(request.notes()));
        session.setReason("Added manually");
        sessionRepository.save(session);
        return SessionResponse.from(session, zone);
    }

    /** Moves / edits a planned session. The edited session is locked so the planner keeps it. */
    @Transactional
    public SessionResponse update(Long userId, Long sessionId, UpdateSessionRequest request) {
        ZoneId zone = zone(userId);
        StudySession session = require(userId, sessionId);
        requireStatus(session, SessionStatus.PLANNED, "Only planned sessions can be edited.");
        validateDateWindow(request.date(), TimeUtils.today(clock, zone));
        Instant start = TimeUtils.toInstant(request.date(), request.startTime(), zone);
        Instant end = start.plusSeconds(request.durationMinutes() * 60L);
        ensureNoConflict(userId, start, end, sessionId, zone);
        session.setTimes(start, end);
        session.setNotes(blankToNull(request.notes()));
        session.setLocked(true);
        return SessionResponse.from(session, zone);
    }

    /** Cancels a planned session (kept for history, excluded from statistics and conflict checks). */
    @Transactional
    public void cancel(Long userId, Long sessionId) {
        StudySession session = require(userId, sessionId);
        requireStatus(session, SessionStatus.PLANNED, "Only planned sessions can be removed.");
        session.setStatus(SessionStatus.CANCELLED);
    }

    @Transactional
    public SessionResponse setLocked(Long userId, Long sessionId, boolean locked) {
        StudySession session = require(userId, sessionId);
        requireStatus(session, SessionStatus.PLANNED, "Only planned sessions can be locked or unlocked.");
        session.setLocked(locked);
        return SessionResponse.from(session, zone(userId));
    }

    @Transactional
    public SessionResponse start(Long userId, Long sessionId) {
        ZoneId zone = zone(userId);
        StudySession session = require(userId, sessionId);
        requireStatus(session, SessionStatus.PLANNED, "Only planned sessions can be started.");
        Instant now = Instant.now(clock);
        if (now.isBefore(session.getStartAt().minusSeconds(EARLY_START_MINUTES * 60L))) {
            throw new BusinessRuleException("This session starts at " + SessionResponse.from(session, zone)
                    .localStartTime() + " on " + TimeUtils.localDate(session.getStartAt(), zone)
                    + ". You can start it at most " + EARLY_START_MINUTES + " minutes early.");
        }
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(now);
        return SessionResponse.from(session, zone);
    }

    /**
     * Completes a session. The duration is validated against the server clock: the student can
     * never claim more minutes than have elapsed since the session (or its start button) began.
     */
    @Transactional
    public SessionActionResponse complete(Long userId, Long sessionId, CompleteSessionRequest request) {
        ZoneId zone = zone(userId);
        StudySession session = require(userId, sessionId);
        Instant now = Instant.now(clock);
        SessionStatus status = session.getStatus();
        boolean lateLogging = status == SessionStatus.MISSED
                && now.isBefore(session.getEndAt().plusSeconds(LATE_COMPLETION_HOURS * 3600L));
        if (status != SessionStatus.PLANNED && status != SessionStatus.IN_PROGRESS && !lateLogging) {
            throw new BusinessRuleException("This session is " + status.name().toLowerCase().replace('_', ' ')
                    + " and cannot be completed.");
        }
        Instant reference = session.getStartedAt() != null ? session.getStartedAt() : session.getStartAt();
        if (now.isBefore(reference)) {
            throw new BusinessRuleException("You can complete this session only after it has started. "
                    + "Use Start to begin early.");
        }
        long elapsedMinutes = Duration.between(reference, now).toMinutes();
        int maxAllowed = (int) Math.min(Math.max(elapsedMinutes, 0), session.getPlannedMinutes() * 2L);

        int actual;
        if (request.actualMinutes() != null) {
            actual = request.actualMinutes();
            if (actual > maxAllowed + 1) {
                throw new BusinessRuleException("Actual time (" + actual + " min) cannot be more than the "
                        + maxAllowed + " minutes that have passed since the session started.");
            }
        } else {
            long focusSeconds = focusRepository.sumSecondsForStudySession(userId, sessionId, FocusStatus.COMPLETED);
            actual = focusSeconds > 0
                    ? (int) Math.ceil(focusSeconds / 60.0)
                    : (int) Math.min(elapsedMinutes, session.getPlannedMinutes());
            actual = Math.min(actual, Math.max(maxAllowed, 1));
        }
        if (actual < 1) {
            throw new BusinessRuleException("No study time has been recorded yet. Enter the minutes you studied.");
        }
        if (session.getSessionType() == SessionType.REVISION && request.revisionRating() == null
                && session.getTopic() != null) {
            throw new BadRequestException("revisionRating", "Rate how well you remembered the topic "
                    + "(Again, Hard, Good or Easy).");
        }

        boolean fullyDone = actual >= Math.ceil(session.getPlannedMinutes() * COMPLETION_THRESHOLD);
        session.setActualMinutes(actual);
        session.setCompletedAt(now);
        if (session.getStartedAt() == null) {
            session.setStartedAt(now.minusSeconds(actual * 60L));
        }
        session.setStatus(fullyDone ? SessionStatus.COMPLETED : SessionStatus.INCOMPLETE);
        if (request.notes() != null && !request.notes().isBlank()) {
            session.setNotes(request.notes().trim());
        }

        Topic topic = session.getTopic();
        LocalDate today = TimeUtils.today(clock, zone);
        if (topic != null) {
            if (session.getSessionType() == SessionType.STUDY) {
                if (Boolean.TRUE.equals(request.topicCompleted())) {
                    topicService.applyStatus(topic, TopicStatus.COMPLETED, today, now);
                } else if (topic.getStatus() == TopicStatus.NOT_STARTED) {
                    topicService.applyStatus(topic, TopicStatus.IN_PROGRESS, today, now);
                }
            } else if (request.revisionRating() != null) {
                revisionService.recordReview(userId, topic, request.revisionRating(), actual, request.notes(),
                        session, today, now);
            }
        }

        SessionResponse rescheduled = null;
        String message;
        if (fullyDone) {
            message = "Great work! Session completed (" + actual + " min).";
        } else {
            SessionRef ref = SessionRef.of(session);
            sessionRepository.flush();
            Map<Long, StudySession> replacements = plannerService.rescheduleAfterChange(userId, List.of(ref));
            StudySession replacement = replacements.get(sessionId);
            rescheduled = replacement == null ? null : SessionResponse.from(replacement, zone);
            message = "Session saved as incomplete (" + actual + " of " + session.getPlannedMinutes() + " min)."
                    + (rescheduled != null
                    ? " The remaining work was rescheduled to " + rescheduled.localDate() + " "
                    + rescheduled.localStartTime() + "."
                    : " The remaining work will be included in your next plan.");
        }
        StudySession reloaded = require(userId, sessionId);
        return new SessionActionResponse(SessionResponse.from(reloaded, zone), rescheduled, message);
    }

    /** Marks a session as missed and immediately reschedules its work when a plan is active. */
    @Transactional
    public SessionActionResponse miss(Long userId, Long sessionId) {
        ZoneId zone = zone(userId);
        StudySession session = require(userId, sessionId);
        if (session.getStatus() != SessionStatus.PLANNED && session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Only planned or in-progress sessions can be marked as missed.");
        }
        session.setStatus(SessionStatus.MISSED);
        SessionRef ref = SessionRef.of(session);
        sessionRepository.flush();

        Map<Long, StudySession> replacements = plannerService.rescheduleAfterChange(userId, List.of(ref));
        StudySession replacement = replacements.get(sessionId);
        SessionResponse rescheduled = replacement == null ? null : SessionResponse.from(replacement, zone);
        String message = rescheduled != null
                ? "Session marked as missed and rescheduled to " + rescheduled.localDate() + " "
                + rescheduled.localStartTime() + "."
                : "Session marked as missed. Its work will be included when you generate your next plan.";
        return new SessionActionResponse(SessionResponse.from(require(userId, sessionId), zone), rescheduled,
                message);
    }

    // ------------------------------------------------------------------ helpers

    private StudySession require(Long userId, Long sessionId) {
        return sessionRepository.findOwned(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Study session", sessionId));
    }

    /**
     * Overlap rule for half-open intervals: two sessions conflict when
     * {@code newStart < existingEnd && existingStart < newEnd}. Back-to-back sessions are allowed.
     * Only planned, in-progress and done sessions occupy time; missed and cancelled ones do not.
     */
    private void ensureNoConflict(Long userId, Instant start, Instant end, Long excludeId, ZoneId zone) {
        List<StudySession> conflicts = sessionRepository.findOverlapping(userId, start, end, SessionStatus.BLOCKING)
                .stream()
                .filter(other -> excludeId == null || !other.getId().equals(excludeId))
                .filter(other -> TimeInterval.overlaps(start, end, other.getStartAt(), other.getEndAt()))
                .toList();
        if (!conflicts.isEmpty()) {
            StudySession first = conflicts.getFirst();
            var from = first.getStartAt().atZone(zone);
            var to = first.getEndAt().atZone(zone);
            throw new ConflictException("This time overlaps another study session on %s from %02d:%02d to %02d:%02d."
                    .formatted(from.toLocalDate(), from.getHour(), from.getMinute(), to.getHour(), to.getMinute()));
        }
    }

    private static void requireStatus(StudySession session, SessionStatus expected, String message) {
        if (session.getStatus() != expected) {
            throw new BusinessRuleException(message);
        }
    }

    private static void validateDateWindow(LocalDate date, LocalDate today) {
        if (date.isBefore(today.minusDays(MANUAL_PAST_DAYS)) || date.isAfter(today.plusDays(MANUAL_FUTURE_DAYS))) {
            throw new BadRequestException("date", "Sessions can be scheduled from " + MANUAL_PAST_DAYS
                    + " days ago up to " + MANUAL_FUTURE_DAYS + " days ahead.");
        }
    }

    private ZoneId zone(Long userId) {
        User user = userService.require(userId);
        return TimeUtils.zone(user.getTimeZone());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
