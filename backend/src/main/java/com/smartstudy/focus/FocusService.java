package com.smartstudy.focus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.smartstudy.common.ScoreMath;
import com.smartstudy.common.TimeUtils;
import com.smartstudy.common.exception.BadRequestException;
import com.smartstudy.common.exception.BusinessRuleException;
import com.smartstudy.common.exception.ConflictException;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.config.SmartStudyProperties;
import com.smartstudy.planner.StudySession;
import com.smartstudy.planner.StudySessionRepository;
import com.smartstudy.planner.engine.TimetableGenerator;
import com.smartstudy.subject.Subject;
import com.smartstudy.subject.SubjectService;
import com.smartstudy.topic.Topic;
import com.smartstudy.topic.TopicService;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pomodoro focus mode. Start, pause, resume and complete are all timestamped by the server;
 * the browser timer is only a display synchronised through {@code serverTime}.
 */
@Service
public class FocusService {

    private final FocusSessionRepository focusRepository;
    private final StudySessionRepository studySessionRepository;
    private final SubjectService subjectService;
    private final TopicService topicService;
    private final UserService userService;
    private final FocusDurationCalculator calculator;
    private final SmartStudyProperties properties;
    private final Clock clock;

    public FocusService(FocusSessionRepository focusRepository, StudySessionRepository studySessionRepository,
                        SubjectService subjectService, TopicService topicService, UserService userService,
                        FocusDurationCalculator calculator, SmartStudyProperties properties, Clock clock) {
        this.focusRepository = focusRepository;
        this.studySessionRepository = studySessionRepository;
        this.subjectService = subjectService;
        this.topicService = topicService;
        this.userService = userService;
        this.calculator = calculator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public FocusSessionResponse start(Long userId, FocusStartRequest request) {
        Instant now = Instant.now(clock);
        Optional<FocusSession> active = findActive(userId, now);
        if (active.isPresent()) {
            throw new ConflictException("A focus session is already running. Complete or cancel it first.");
        }
        User user = userService.require(userId);
        int planned = request.plannedMinutes() != null ? request.plannedMinutes() : user.getFocusMinutes();
        if (planned > properties.getFocus().getMaxPlannedMinutes()) {
            throw new BadRequestException("plannedMinutes", "A focus block can last at most "
                    + properties.getFocus().getMaxPlannedMinutes() + " minutes.");
        }

        FocusSession session = new FocusSession(userId, planned, now);
        Subject subject = null;
        if (request.studySessionId() != null) {
            StudySession studySession = studySessionRepository.findOwned(request.studySessionId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Study session", request.studySessionId()));
            session.setStudySession(studySession);
            subject = studySession.getSubject();
            if (studySession.getTopic() != null && request.topicId() == null) {
                session.setTopic(studySession.getTopic());
            }
        }
        if (request.topicId() != null) {
            Topic topic = topicService.require(userId, request.topicId());
            session.setTopic(topic);
            subject = topic.getSubject();
        }
        if (subject == null && request.subjectId() != null) {
            subject = subjectService.require(userId, request.subjectId());
        }
        session.setSubject(subject);
        focusRepository.save(session);
        return toResponse(session, now);
    }

    @Transactional
    public FocusSessionResponse pause(Long userId, Long focusId) {
        Instant now = Instant.now(clock);
        FocusSession session = require(userId, focusId);
        if (session.getStatus() != FocusStatus.RUNNING) {
            throw new BusinessRuleException("Only a running focus session can be paused.");
        }
        session.setStatus(FocusStatus.PAUSED);
        session.setPausedAt(now);
        session.setPauseCount(session.getPauseCount() + 1);
        return toResponse(session, now);
    }

    @Transactional
    public FocusSessionResponse resume(Long userId, Long focusId) {
        Instant now = Instant.now(clock);
        FocusSession session = require(userId, focusId);
        if (session.getStatus() != FocusStatus.PAUSED) {
            throw new BusinessRuleException("Only a paused focus session can be resumed.");
        }
        closePause(session, now);
        session.setStatus(FocusStatus.RUNNING);
        return toResponse(session, now);
    }

    @Transactional
    public FocusCompleteResponse complete(Long userId, Long focusId, FocusCompleteRequest request) {
        Instant now = Instant.now(clock);
        FocusSession session = require(userId, focusId);
        if (!FocusStatus.ACTIVE.contains(session.getStatus())) {
            throw new BusinessRuleException("This focus session has already ended.");
        }
        finish(session, now, FocusStatus.COMPLETED, request == null ? null : request.clientReportedSeconds());
        if (request != null && request.notes() != null && !request.notes().isBlank()) {
            session.setNotes(request.notes().trim());
        }
        User user = userService.require(userId);
        int completedToday = completedOn(userId, TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone())),
                TimeUtils.zone(user.getTimeZone()));
        boolean longBreak = completedToday > 0 && completedToday % user.getLongBreakInterval() == 0;
        return new FocusCompleteResponse(toResponse(session, now), completedToday, longBreak ? "LONG" : "SHORT",
                longBreak ? user.getLongBreakMinutes() : user.getShortBreakMinutes());
    }

    @Transactional
    public FocusSessionResponse cancel(Long userId, Long focusId) {
        Instant now = Instant.now(clock);
        FocusSession session = require(userId, focusId);
        if (!FocusStatus.ACTIVE.contains(session.getStatus())) {
            throw new BusinessRuleException("This focus session has already ended.");
        }
        finish(session, now, FocusStatus.CANCELLED, null);
        return toResponse(session, now);
    }

    /** The running or paused session, if any (auto-completing one that was abandoned long ago). */
    @Transactional
    public Optional<FocusSessionResponse> active(Long userId) {
        Instant now = Instant.now(clock);
        return findActive(userId, now).map(session -> toResponse(session, now));
    }

    @Transactional(readOnly = true)
    public List<FocusSessionResponse> history(Long userId, int days) {
        Instant now = Instant.now(clock);
        int range = Math.max(1, Math.min(365, days));
        return focusRepository.findStartedSince(userId, now.minus(Duration.ofDays(range))).stream()
                .map(session -> toResponse(session, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public FocusStatsResponse stats(Long userId) {
        User user = userService.require(userId);
        ZoneId zone = TimeUtils.zone(user.getTimeZone());
        LocalDate today = TimeUtils.today(clock, zone);
        LocalDate from = today.minusDays(29);
        LocalDate weekStart = TimetableGenerator.weekStart(today);
        List<FocusSession> sessions = focusRepository.findStartedSince(userId, TimeUtils.startOfDay(from, zone))
                .stream().filter(s -> s.getStatus() == FocusStatus.COMPLETED).toList();

        Map<LocalDate, int[]> daily = new LinkedHashMap<>();
        for (LocalDate date = today.minusDays(6); !date.isAfter(today); date = date.plusDays(1)) {
            daily.put(date, new int[2]);
        }
        Map<Long, Object[]> bySubject = new LinkedHashMap<>();
        int todayMinutes = 0;
        int todaySessions = 0;
        int weekMinutes = 0;
        int totalSeconds = 0;
        for (FocusSession session : sessions) {
            int seconds = session.getActualFocusSeconds() == null ? 0 : session.getActualFocusSeconds();
            int minutes = seconds / 60;
            totalSeconds += seconds;
            LocalDate date = TimeUtils.localDate(session.getStartedAt(), zone);
            if (date.equals(today)) {
                todayMinutes += minutes;
                todaySessions++;
            }
            if (!date.isBefore(weekStart)) {
                weekMinutes += minutes;
            }
            int[] day = daily.get(date);
            if (day != null) {
                day[0] += minutes;
                day[1]++;
            }
            if (session.getSubject() != null) {
                Object[] acc = bySubject.computeIfAbsent(session.getSubject().getId(),
                        id -> new Object[]{session.getSubject().getName(), session.getSubject().getColor(), 0});
                acc[2] = (Integer) acc[2] + minutes;
            }
        }
        List<FocusStatsResponse.DailyFocus> dailyList = new ArrayList<>();
        daily.forEach((date, values) -> dailyList.add(new FocusStatsResponse.DailyFocus(date, values[0], values[1])));
        List<FocusStatsResponse.SubjectFocus> subjectList = new ArrayList<>();
        bySubject.forEach((id, acc) -> subjectList.add(new FocusStatsResponse.SubjectFocus(id, (String) acc[0],
                (String) acc[1], (Integer) acc[2])));

        int interval = user.getLongBreakInterval();
        int inCycle = todaySessions % interval;
        boolean nextLong = todaySessions > 0 && inCycle == 0;
        double average = sessions.isEmpty() ? 0.0 : ScoreMath.round2(totalSeconds / 60.0 / sessions.size());
        return new FocusStatsResponse(todayMinutes, todaySessions, weekMinutes, sessions.size(), totalSeconds / 60,
                average, inCycle, interval, nextLong ? "LONG" : "SHORT", user.getFocusMinutes(),
                user.getShortBreakMinutes(), user.getLongBreakMinutes(), dailyList, subjectList);
    }

    // ------------------------------------------------------------------ helpers

    private Optional<FocusSession> findActive(Long userId, Instant now) {
        List<FocusSession> active = focusRepository.findByStatuses(userId, FocusStatus.ACTIVE);
        FocusSession current = null;
        for (FocusSession session : active) {
            long elapsed = calculator.elapsedFocusSeconds(session.getStartedAt(), now, session.getTotalPausedSeconds(),
                    session.getPausedAt());
            long limit = session.getPlannedMinutes() * 60L + properties.getFocus().getAutoCompleteGraceSeconds();
            boolean abandonedWhilePaused = session.getStatus() == FocusStatus.PAUSED
                    && session.getPausedAt() != null
                    && Duration.between(session.getPausedAt(), now).toHours() >= 12;
            if (session.getStatus() == FocusStatus.RUNNING && elapsed >= limit) {
                finish(session, now, FocusStatus.COMPLETED, null);
            } else if (abandonedWhilePaused) {
                finish(session, now, FocusStatus.COMPLETED, null);
            } else if (current == null) {
                current = session;
            }
        }
        return Optional.ofNullable(current);
    }

    private void finish(FocusSession session, Instant now, FocusStatus status, Integer clientReportedSeconds) {
        int actual = calculator.actualFocusSeconds(session.getStartedAt(), now, session.getTotalPausedSeconds(),
                session.getPausedAt(), session.getPlannedMinutes(), clientReportedSeconds);
        if (session.getStatus() == FocusStatus.PAUSED) {
            closePause(session, now);
        }
        session.setClientReportedSeconds(clientReportedSeconds);
        session.setActualFocusSeconds(actual);
        session.setEndedAt(now);
        session.setStatus(status);
    }

    private static void closePause(FocusSession session, Instant now) {
        if (session.getPausedAt() != null) {
            long paused = Math.max(0, Duration.between(session.getPausedAt(), now).getSeconds());
            session.setTotalPausedSeconds((int) Math.min(Integer.MAX_VALUE, session.getTotalPausedSeconds() + paused));
            session.setPausedAt(null);
        }
    }

    private int completedOn(Long userId, LocalDate date, ZoneId zone) {
        return (int) focusRepository.findStartedSince(userId, TimeUtils.startOfDay(date, zone)).stream()
                .filter(s -> s.getStatus() == FocusStatus.COMPLETED)
                .filter(s -> TimeUtils.localDate(s.getStartedAt(), zone).equals(date))
                .count();
    }

    private FocusSession require(Long userId, Long focusId) {
        return focusRepository.findOwned(focusId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Focus session", focusId));
    }

    private FocusSessionResponse toResponse(FocusSession session, Instant now) {
        long elapsed = FocusStatus.ACTIVE.contains(session.getStatus())
                ? calculator.elapsedFocusSeconds(session.getStartedAt(), now, session.getTotalPausedSeconds(),
                session.getPausedAt())
                : (session.getActualFocusSeconds() == null ? 0 : session.getActualFocusSeconds());
        long remaining = FocusStatus.ACTIVE.contains(session.getStatus())
                ? calculator.remainingSeconds(elapsed, session.getPlannedMinutes())
                : 0;
        return new FocusSessionResponse(session.getId(), session.getStatus(),
                session.getSubject() == null ? null : session.getSubject().getId(),
                session.getSubject() == null ? null : session.getSubject().getName(),
                session.getSubject() == null ? null : session.getSubject().getColor(),
                session.getTopic() == null ? null : session.getTopic().getId(),
                session.getTopic() == null ? null : session.getTopic().getName(),
                session.getStudySession() == null ? null : session.getStudySession().getId(),
                session.getPlannedMinutes(), session.getStartedAt(), session.getPausedAt(),
                session.getTotalPausedSeconds(), session.getPauseCount(), session.getEndedAt(),
                session.getActualFocusSeconds(), elapsed, remaining, session.getNotes(), now);
    }
}
