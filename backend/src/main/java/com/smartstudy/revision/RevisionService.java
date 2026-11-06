package com.smartstudy.revision;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.smartstudy.common.ScoreMath;
import com.smartstudy.common.TimeUtils;
import com.smartstudy.common.exception.BadRequestException;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.performance.MasteryLevel;
import com.smartstudy.performance.TopicMastery;
import com.smartstudy.performance.TopicMasteryRepository;
import com.smartstudy.planner.SessionStatus;
import com.smartstudy.planner.SessionType;
import com.smartstudy.planner.StudySession;
import com.smartstudy.planner.StudySessionRepository;
import com.smartstudy.planner.engine.TimetableGenerator;
import com.smartstudy.topic.Topic;
import com.smartstudy.topic.TopicRepository;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spaced-repetition queue, review recording and revision history. */
@Service
public class RevisionService {

    private static final int UPCOMING_DAYS = 7;

    private final TopicRepository topicRepository;
    private final TopicMasteryRepository masteryRepository;
    private final RevisionLogRepository logRepository;
    private final StudySessionRepository sessionRepository;
    private final UserService userService;
    private final SpacedRepetitionScheduler scheduler;
    private final Clock clock;

    public RevisionService(TopicRepository topicRepository, TopicMasteryRepository masteryRepository,
                           RevisionLogRepository logRepository, StudySessionRepository sessionRepository,
                           UserService userService, SpacedRepetitionScheduler scheduler, Clock clock) {
        this.topicRepository = topicRepository;
        this.masteryRepository = masteryRepository;
        this.logRepository = logRepository;
        this.sessionRepository = sessionRepository;
        this.userService = userService;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RevisionDueResponse due(Long userId) {
        User user = userService.require(userId);
        LocalDate today = TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
        Map<Long, TopicMastery> mastery = new HashMap<>();
        for (TopicMastery item : masteryRepository.findAllForUser(userId)) {
            mastery.put(item.getTopic().getId(), item);
        }
        List<RevisionItemResponse> overdue = new ArrayList<>();
        List<RevisionItemResponse> dueToday = new ArrayList<>();
        List<RevisionItemResponse> upcoming = new ArrayList<>();
        for (Topic topic : topicRepository.findRevisionsDueBy(userId, today.plusDays(UPCOMING_DAYS))) {
            RevisionItemResponse item = toItem(topic, today, mastery.get(topic.getId()));
            switch (item.dueStatus()) {
                case "OVERDUE" -> overdue.add(item);
                case "DUE_TODAY" -> dueToday.add(item);
                default -> upcoming.add(item);
            }
        }
        LocalDate weekStart = TimetableGenerator.weekStart(today);
        int completedThisWeek = logRepository.findBetween(userId, weekStart, today).size();
        return new RevisionDueResponse(today, overdue.size() + dueToday.size(), overdue.size(), upcoming.size(),
                completedThisWeek, overdue, dueToday, upcoming);
    }

    /**
     * Records a review from the revision page. If a planned revision session for this topic exists
     * today (or the request names one), that session is completed as well.
     */
    @Transactional
    public RevisionResultResponse complete(Long userId, Long topicId, CompleteRevisionRequest request) {
        User user = userService.require(userId);
        ZoneId zone = TimeUtils.zone(user.getTimeZone());
        LocalDate today = TimeUtils.today(clock, zone);
        Instant now = Instant.now(clock);
        Topic topic = topicRepository.findOwnedWithSubject(topicId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", topicId));

        StudySession linked = null;
        if (request.studySessionId() != null) {
            linked = sessionRepository.findOwned(request.studySessionId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Study session", request.studySessionId()));
            if (linked.getTopic() == null || !linked.getTopic().getId().equals(topicId)) {
                throw new BadRequestException("studySessionId", "The study session belongs to a different topic.");
            }
        } else {
            linked = sessionRepository.findTopicSessionsInRange(userId, topicId, SessionType.REVISION,
                            EnumSet.of(SessionStatus.PLANNED, SessionStatus.IN_PROGRESS),
                            TimeUtils.startOfDay(today, zone), TimeUtils.startOfDay(today.plusDays(1), zone))
                    .stream().findFirst().orElse(null);
        }

        int previousInterval = topic.getRevisionIntervalDays();
        double previousEase = topic.getRevisionEaseFactor().doubleValue();
        RecordedReview review = recordReview(userId, topic, request.rating(), request.durationMinutes(),
                request.notes(), linked, today, now);
        RevisionLog log = review.log();

        Long completedSessionId = null;
        if (linked != null && (linked.getStatus() == SessionStatus.PLANNED
                || linked.getStatus() == SessionStatus.IN_PROGRESS)) {
            int minutes = request.durationMinutes() != null ? request.durationMinutes() : linked.getPlannedMinutes();
            linked.setActualMinutes(minutes);
            linked.setCompletedAt(now);
            if (linked.getStartedAt() == null) {
                linked.setStartedAt(now.minusSeconds(minutes * 60L));
            }
            linked.setStatus(minutes >= Math.ceil(linked.getPlannedMinutes() * 0.8)
                    ? SessionStatus.COMPLETED : SessionStatus.INCOMPLETE);
            completedSessionId = linked.getId();
        }
        return new RevisionResultResponse(topic.getId(), topic.getName(), request.rating(), today, previousInterval,
                log.getNewIntervalDays(), previousEase, log.getNewEaseFactor().doubleValue(), log.getRepetitions(),
                log.getNextRevisionDate(), review.explanation(), completedSessionId);
    }

    /**
     * Applies the SM-2 update to the topic and stores the log entry. Shared with the study-session
     * service so that completing a REVISION session updates the schedule the same way.
     */
    @Transactional
    public RecordedReview recordReview(Long userId, Topic topic, RevisionRating rating, Integer durationMinutes,
                                       String notes, StudySession linkedSession, LocalDate today, Instant now) {
        SpacedRepetitionScheduler.RevisionState state = new SpacedRepetitionScheduler.RevisionState(
                topic.getRevisionEaseFactor().doubleValue(), topic.getRevisionIntervalDays(),
                topic.getRevisionRepetitions());
        SpacedRepetitionScheduler.ReviewResult result = scheduler.review(state, rating, today);

        BigDecimal previousEase = topic.getRevisionEaseFactor();
        BigDecimal newEase = BigDecimal.valueOf(result.easeFactor()).setScale(2, RoundingMode.HALF_UP);
        RevisionLog log = new RevisionLog(userId, topic, rating, today, topic.getNextRevisionDate(), now,
                previousEase, newEase, topic.getRevisionIntervalDays(), result.intervalDays(), result.repetitions(),
                result.nextRevisionDate());
        log.setDurationMinutes(durationMinutes == null ? null : Math.max(1, Math.min(600, durationMinutes)));
        log.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        log.setStudySession(linkedSession);
        logRepository.save(log);

        topic.setRevisionEaseFactor(newEase);
        topic.setRevisionIntervalDays(result.intervalDays());
        topic.setRevisionRepetitions(result.repetitions());
        topic.setNextRevisionDate(result.nextRevisionDate());
        topic.setLastRevisedOn(today);
        return new RecordedReview(log, result.explanation());
    }

    /** A stored review together with the human-readable explanation of the new schedule. */
    public record RecordedReview(RevisionLog log, String explanation) {
    }

    @Transactional(readOnly = true)
    public List<RevisionLogResponse> history(Long userId, Long topicId, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(200, limit)));
        List<RevisionLog> logs = topicId == null
                ? logRepository.findRecent(userId, page)
                : logRepository.findRecentForTopic(userId, topicId, page);
        return logs.stream().map(RevisionLogResponse::from).toList();
    }

    private static RevisionItemResponse toItem(Topic topic, LocalDate today, TopicMastery mastery) {
        int daysOverdue = (int) ChronoUnit.DAYS.between(topic.getNextRevisionDate(), today);
        String status = daysOverdue > 0 ? "OVERDUE" : daysOverdue == 0 ? "DUE_TODAY" : "UPCOMING";
        Double score = mastery == null ? null : mastery.getMasteryScore().doubleValue();
        return new RevisionItemResponse(topic.getId(), topic.getName(), topic.getSubject().getId(),
                topic.getSubject().getName(), topic.getSubject().getColor(), topic.getNextRevisionDate(), daysOverdue,
                status, topic.getRevisionIntervalDays(), topic.getRevisionRepetitions(),
                ScoreMath.round2(topic.getRevisionEaseFactor().doubleValue()), topic.getLastRevisedOn(), score,
                MasteryLevel.of(score).name());
    }
}
