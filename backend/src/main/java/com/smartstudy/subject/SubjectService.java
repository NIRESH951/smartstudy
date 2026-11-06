package com.smartstudy.subject;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.smartstudy.common.ScoreMath;
import com.smartstudy.common.TimeUtils;
import com.smartstudy.common.exception.ConflictException;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.exam.Exam;
import com.smartstudy.exam.ExamRepository;
import com.smartstudy.performance.TopicMastery;
import com.smartstudy.performance.TopicMasteryRepository;
import com.smartstudy.topic.TopicRepository;
import com.smartstudy.topic.TopicStatus;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Subject management. Every query is scoped to the signed-in student's id. */
@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final TopicMasteryRepository masteryRepository;
    private final ExamRepository examRepository;
    private final UserService userService;
    private final Clock clock;

    public SubjectService(SubjectRepository subjectRepository, TopicRepository topicRepository,
                          TopicMasteryRepository masteryRepository, ExamRepository examRepository,
                          UserService userService, Clock clock) {
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.masteryRepository = masteryRepository;
        this.examRepository = examRepository;
        this.userService = userService;
        this.clock = clock;
    }

    /** Loads a subject owned by the student or fails with 404 (also for other students' ids). */
    @Transactional(readOnly = true)
    public Subject require(Long userId, Long subjectId) {
        return subjectRepository.findByIdAndUserId(subjectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", subjectId));
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> list(Long userId) {
        List<Subject> subjects = subjectRepository.findByUserIdOrderByNameAsc(userId);
        Stats stats = loadStats(userId);
        return subjects.stream().map(subject -> toResponse(subject, stats)).toList();
    }

    @Transactional(readOnly = true)
    public SubjectResponse get(Long userId, Long subjectId) {
        return toResponse(require(userId, subjectId), loadStats(userId));
    }

    @Transactional
    public SubjectResponse create(Long userId, SubjectRequest request) {
        String name = request.name().trim();
        if (subjectRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("You already have a subject named \"" + name + "\".");
        }
        Subject subject = new Subject(userId, name);
        apply(subject, request);
        subjectRepository.save(subject);
        return toResponse(subject, loadStats(userId));
    }

    @Transactional
    public SubjectResponse update(Long userId, Long subjectId, SubjectRequest request) {
        Subject subject = require(userId, subjectId);
        String name = request.name().trim();
        if (subjectRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, subjectId)) {
            throw new ConflictException("You already have a subject named \"" + name + "\".");
        }
        subject.setName(name);
        apply(subject, request);
        return toResponse(subject, loadStats(userId));
    }

    /** Deletes the subject; topics, exams, sessions and assessments are removed by ON DELETE CASCADE. */
    @Transactional
    public void delete(Long userId, Long subjectId) {
        Subject subject = require(userId, subjectId);
        subjectRepository.delete(subject);
        subjectRepository.flush();
    }

    private void apply(Subject subject, SubjectRequest request) {
        subject.setCode(blankToNull(request.code()));
        subject.setColor(request.color() == null || request.color().isBlank() ? "#10B981" : request.color());
        subject.setImportance(request.importance());
        subject.setTargetScore(request.targetScore());
        subject.setDescription(blankToNull(request.description()));
        if (request.archived() != null) {
            subject.setArchived(request.archived());
        }
    }

    private Stats loadStats(Long userId) {
        User user = userService.require(userId);
        LocalDate today = TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
        Map<Long, long[]> topicCounts = new HashMap<>();
        for (Object[] row : topicRepository.countBySubject(userId, TopicStatus.COMPLETED)) {
            topicCounts.put((Long) row[0], new long[]{toLong(row[1]), toLong(row[2])});
        }
        Map<Long, double[]> mastery = new HashMap<>();
        for (TopicMastery item : masteryRepository.findAllWithTopic(userId)) {
            Long subjectId = item.getTopic().getSubject().getId();
            double[] acc = mastery.computeIfAbsent(subjectId, id -> new double[3]);
            double score = item.getMasteryScore().doubleValue();
            acc[0] += score;
            acc[1] += 1;
            if (score < 40.0) {
                acc[2] += 1;
            }
        }
        Map<Long, Exam> nextExam = new HashMap<>();
        for (Exam exam : examRepository.findUpcoming(userId, today)) {
            nextExam.putIfAbsent(exam.getSubject().getId(), exam);
        }
        return new Stats(today, topicCounts, mastery, nextExam);
    }

    private SubjectResponse toResponse(Subject subject, Stats stats) {
        long[] counts = stats.topicCounts().getOrDefault(subject.getId(), new long[]{0, 0});
        int topicCount = (int) counts[0];
        int completed = (int) counts[1];
        double syllabus = topicCount == 0 ? 0.0 : ScoreMath.round2(100.0 * completed / topicCount);
        double[] mastery = stats.mastery().get(subject.getId());
        Double averageMastery = mastery == null || mastery[1] == 0 ? null : ScoreMath.round2(mastery[0] / mastery[1]);
        int assessed = mastery == null ? 0 : (int) mastery[1];
        int weak = mastery == null ? 0 : (int) mastery[2];
        Exam exam = stats.nextExam().get(subject.getId());
        return new SubjectResponse(subject.getId(), subject.getName(), subject.getCode(), subject.getColor(),
                subject.getImportance(), ScoreMath.toDouble(subject.getTargetScore()), subject.getDescription(),
                subject.isArchived(), topicCount, completed, syllabus, averageMastery, assessed, weak,
                exam == null ? null : exam.getId(),
                exam == null ? null : exam.getTitle(),
                exam == null ? null : exam.getExamDate(),
                exam == null ? null : (int) ChronoUnit.DAYS.between(stats.today(), exam.getExamDate()),
                subject.getCreatedAt());
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Stats(LocalDate today, Map<Long, long[]> topicCounts, Map<Long, double[]> mastery,
                         Map<Long, Exam> nextExam) {
    }
}
