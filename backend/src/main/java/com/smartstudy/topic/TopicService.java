package com.smartstudy.topic;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.smartstudy.common.ScoreMath;
import com.smartstudy.common.TimeUtils;
import com.smartstudy.common.exception.ConflictException;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.performance.MasteryLevel;
import com.smartstudy.performance.TopicMastery;
import com.smartstudy.performance.TopicMasteryRepository;
import com.smartstudy.planner.SessionStatus;
import com.smartstudy.planner.SessionType;
import com.smartstudy.planner.StudySessionRepository;
import com.smartstudy.revision.SpacedRepetitionScheduler;
import com.smartstudy.subject.Subject;
import com.smartstudy.subject.SubjectService;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Topic management, syllabus status and entry into the revision cycle. */
@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final SubjectService subjectService;
    private final TopicMasteryRepository masteryRepository;
    private final StudySessionRepository sessionRepository;
    private final UserService userService;
    private final SpacedRepetitionScheduler scheduler;
    private final Clock clock;

    public TopicService(TopicRepository topicRepository, SubjectService subjectService,
                        TopicMasteryRepository masteryRepository, StudySessionRepository sessionRepository,
                        UserService userService, SpacedRepetitionScheduler scheduler, Clock clock) {
        this.topicRepository = topicRepository;
        this.subjectService = subjectService;
        this.masteryRepository = masteryRepository;
        this.sessionRepository = sessionRepository;
        this.userService = userService;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** Loads a topic (with its subject) owned by the student, or fails with 404. */
    @Transactional(readOnly = true)
    public Topic require(Long userId, Long topicId) {
        return topicRepository.findOwnedWithSubject(topicId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", topicId));
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> listBySubject(Long userId, Long subjectId) {
        subjectService.require(userId, subjectId);
        return toResponses(userId, topicRepository.findBySubjectWithSubject(userId, subjectId));
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> listAll(Long userId) {
        return toResponses(userId, topicRepository.findAllWithSubject(userId));
    }

    @Transactional(readOnly = true)
    public TopicResponse get(Long userId, Long topicId) {
        return toResponses(userId, List.of(require(userId, topicId))).getFirst();
    }

    @Transactional
    public TopicResponse create(Long userId, Long subjectId, TopicRequest request) {
        Subject subject = subjectService.require(userId, subjectId);
        String name = request.name().trim();
        if (topicRepository.existsBySubjectIdAndNameIgnoreCase(subjectId, name)) {
            throw new ConflictException("This subject already has a topic named \"" + name + "\".");
        }
        Topic topic = new Topic(userId, subject, name);
        topic.setDescription(blankToNull(request.description()));
        topic.setDifficulty(request.difficulty());
        topic.setEstimatedMinutes(request.estimatedMinutes());
        topic.setSortOrder(topicRepository.findMaxSortOrder(subjectId) + 1);
        topicRepository.save(topic);
        if (request.status() != null && request.status() != TopicStatus.NOT_STARTED) {
            applyStatus(topic, request.status(), userId);
        }
        return toResponses(userId, List.of(topic)).getFirst();
    }

    @Transactional
    public TopicResponse update(Long userId, Long topicId, TopicRequest request) {
        Topic topic = require(userId, topicId);
        String name = request.name().trim();
        if (topicRepository.existsBySubjectIdAndNameIgnoreCaseAndIdNot(topic.getSubject().getId(), name, topicId)) {
            throw new ConflictException("This subject already has a topic named \"" + name + "\".");
        }
        topic.setName(name);
        topic.setDescription(blankToNull(request.description()));
        topic.setDifficulty(request.difficulty());
        topic.setEstimatedMinutes(request.estimatedMinutes());
        if (request.status() != null && request.status() != topic.getStatus()) {
            applyStatus(topic, request.status(), userId);
        }
        return toResponses(userId, List.of(topic)).getFirst();
    }

    @Transactional
    public TopicResponse updateStatus(Long userId, Long topicId, TopicStatus status) {
        Topic topic = require(userId, topicId);
        applyStatus(topic, status, userId);
        return toResponses(userId, List.of(topic)).getFirst();
    }

    @Transactional
    public void delete(Long userId, Long topicId) {
        Topic topic = require(userId, topicId);
        topicRepository.delete(topic);
        topicRepository.flush();
    }

    /**
     * Changes the syllabus status. Completing a topic for the first time puts it into the
     * spaced-repetition cycle with the first revision due tomorrow (student's local date).
     */
    @Transactional
    public void applyStatus(Topic topic, TopicStatus status, Long userId) {
        User user = userService.require(userId);
        LocalDate today = TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
        Instant now = Instant.now(clock);
        applyStatus(topic, status, today, now);
    }

    /** Pure state transition, reused by the study-session service. */
    public void applyStatus(Topic topic, TopicStatus status, LocalDate today, Instant now) {
        topic.setStatus(status);
        if (status == TopicStatus.COMPLETED) {
            if (topic.getCompletedAt() == null) {
                topic.setCompletedAt(now);
            }
            if (topic.getNextRevisionDate() == null) {
                topic.setNextRevisionDate(scheduler.firstReviewDate(today));
            }
        } else {
            topic.setCompletedAt(null);
        }
    }

    private List<TopicResponse> toResponses(Long userId, List<Topic> topics) {
        Map<Long, TopicMastery> mastery = new HashMap<>();
        for (TopicMastery item : masteryRepository.findAllForUser(userId)) {
            mastery.put(item.getTopic().getId(), item);
        }
        Map<Long, Integer> studied = new HashMap<>();
        for (Object[] row : sessionRepository.sumActualMinutesByTopic(userId, SessionType.STUDY,
                SessionStatus.DONE)) {
            studied.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return topics.stream().map(topic -> {
            TopicMastery m = mastery.get(topic.getId());
            Double score = m == null ? null : m.getMasteryScore().doubleValue();
            return new TopicResponse(topic.getId(), topic.getSubject().getId(), topic.getSubject().getName(),
                    topic.getSubject().getColor(), topic.getName(), topic.getDescription(), topic.getDifficulty(),
                    topic.getEstimatedMinutes(), studied.getOrDefault(topic.getId(), 0), topic.getStatus(),
                    topic.getCompletedAt(), topic.getSortOrder(), score, MasteryLevel.of(score).name(),
                    m == null ? 0 : m.getAssessmentCount(), topic.getNextRevisionDate(), topic.getLastRevisedOn(),
                    topic.getRevisionIntervalDays(), topic.getRevisionRepetitions(),
                    ScoreMath.round2(topic.getRevisionEaseFactor().doubleValue()), topic.getCreatedAt());
        }).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
