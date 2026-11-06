package com.smartstudy.readiness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.smartstudy.assessment.Assessment;
import com.smartstudy.assessment.AssessmentRepository;
import com.smartstudy.assessment.AssessmentType;
import com.smartstudy.common.ScoreMath;
import com.smartstudy.common.TimeUtils;
import com.smartstudy.exam.Exam;
import com.smartstudy.exam.ExamRepository;
import com.smartstudy.exam.ExamService;
import com.smartstudy.performance.MasteryLevel;
import com.smartstudy.performance.TopicMastery;
import com.smartstudy.performance.TopicMasteryRepository;
import com.smartstudy.topic.Topic;
import com.smartstudy.topic.TopicRepository;
import com.smartstudy.topic.TopicStatus;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes exam readiness from real progress data and stores one snapshot per exam per day.
 *
 * <ul>
 *   <li><b>Syllabus completion</b> = completed topics / all topics of the subject.</li>
 *   <li><b>Topic mastery</b> = average mastery of the subject's <em>assessed</em> topics
 *       (unavailable when none is assessed).</li>
 *   <li><b>Mock test performance</b> = average percentage of the latest three MOCK_TEST
 *       assessments of the subject (unavailable when there are none).</li>
 *   <li><b>Revision completion</b> = share of completed topics that have been revised at least once
 *       and are not overdue (unavailable when no topic is completed yet).</li>
 * </ul>
 */
@Service
public class ReadinessService {

    static final int MOCK_TESTS_CONSIDERED = 3;
    static final String DISCLAIMER = "This is an estimate of your preparation based on your recorded progress, "
            + "not a prediction or guarantee of your actual exam result.";

    private final ExamService examService;
    private final ExamRepository examRepository;
    private final TopicRepository topicRepository;
    private final TopicMasteryRepository masteryRepository;
    private final AssessmentRepository assessmentRepository;
    private final ReadinessSnapshotRepository snapshotRepository;
    private final ReadinessCalculator calculator;
    private final UserService userService;
    private final Clock clock;

    public ReadinessService(ExamService examService, ExamRepository examRepository, TopicRepository topicRepository,
                            TopicMasteryRepository masteryRepository, AssessmentRepository assessmentRepository,
                            ReadinessSnapshotRepository snapshotRepository, ReadinessCalculator calculator,
                            UserService userService, Clock clock) {
        this.examService = examService;
        this.examRepository = examRepository;
        this.topicRepository = topicRepository;
        this.masteryRepository = masteryRepository;
        this.assessmentRepository = assessmentRepository;
        this.snapshotRepository = snapshotRepository;
        this.calculator = calculator;
        this.userService = userService;
        this.clock = clock;
    }

    /** Full readiness report of one exam; refreshes today's snapshot while the exam is upcoming. */
    @Transactional
    public ReadinessResponse forExam(Long userId, Long examId) {
        Exam exam = examService.require(userId, examId);
        LocalDate today = today(userId);
        Evaluation evaluation = evaluate(userId, exam, today);
        if (!exam.getExamDate().isBefore(today)) {
            saveSnapshot(userId, exam, today, evaluation);
        }
        List<ReadinessResponse.TrendPoint> history = snapshotRepository.findHistory(userId, examId).stream()
                .map(s -> new ReadinessResponse.TrendPoint(s.getSnapshotDate(), ScoreMath.toDouble(s.getReadinessScore())))
                .toList();
        return toResponse(exam, today, evaluation, history);
    }

    /** Readiness of all upcoming exams (dashboard and exam list). */
    @Transactional
    public List<ReadinessResponse.OverviewItem> overview(Long userId) {
        LocalDate today = today(userId);
        List<ReadinessResponse.OverviewItem> items = new ArrayList<>();
        for (Exam exam : examRepository.findUpcoming(userId, today)) {
            Evaluation evaluation = evaluate(userId, exam, today);
            saveSnapshot(userId, exam, today, evaluation);
            items.add(new ReadinessResponse.OverviewItem(exam.getId(), exam.getTitle(), exam.getSubject().getId(),
                    exam.getSubject().getName(), exam.getSubject().getColor(), exam.getExamDate(),
                    (int) ChronoUnit.DAYS.between(today, exam.getExamDate()), evaluation.result().readinessScore(),
                    evaluation.result().level(), evaluation.result().dataSufficiency()));
        }
        return items;
    }

    /** Stores today's snapshot for every upcoming exam (used by the nightly background job). */
    @Transactional
    public int snapshotUpcoming(Long userId) {
        return overview(userId).size();
    }

    private Evaluation evaluate(Long userId, Exam exam, LocalDate today) {
        Long subjectId = exam.getSubject().getId();
        List<Topic> topics = topicRepository.findBySubjectWithSubject(userId, subjectId);
        Map<Long, TopicMastery> mastery = new HashMap<>();
        for (TopicMastery item : masteryRepository.findAllForUser(userId)) {
            mastery.put(item.getTopic().getId(), item);
        }

        int total = topics.size();
        int completed = 0;
        int assessed = 0;
        double masterySum = 0;
        int revisedOnSchedule = 0;
        int overdueRevisions = 0;
        int notAssessed = 0;
        List<ReadinessResponse.PendingTopic> pending = new ArrayList<>();
        List<ReadinessResponse.FocusArea> weak = new ArrayList<>();
        List<ReadinessResponse.FocusArea> needsImprovement = new ArrayList<>();
        for (Topic topic : topics) {
            TopicMastery m = mastery.get(topic.getId());
            Double score = m == null ? null : m.getMasteryScore().doubleValue();
            if (score != null) {
                assessed++;
                masterySum += score;
                MasteryLevel level = MasteryLevel.of(score);
                if (level == MasteryLevel.WEAK) {
                    weak.add(new ReadinessResponse.FocusArea("WEAK_TOPIC", topic.getName(),
                            "Mastery %.0f%% (weak) - re-study and test yourself again.".formatted(score),
                            topic.getId()));
                } else if (level == MasteryLevel.NEEDS_IMPROVEMENT) {
                    needsImprovement.add(new ReadinessResponse.FocusArea("NEEDS_IMPROVEMENT", topic.getName(),
                            "Mastery %.0f%% - practise more problems.".formatted(score), topic.getId()));
                }
            } else {
                notAssessed++;
            }
            boolean overdue = topic.getNextRevisionDate() != null && topic.getNextRevisionDate().isBefore(today);
            if (overdue) {
                overdueRevisions++;
            }
            if (topic.getStatus() == TopicStatus.COMPLETED) {
                completed++;
                if (topic.getLastRevisedOn() != null && !overdue) {
                    revisedOnSchedule++;
                }
            } else {
                pending.add(new ReadinessResponse.PendingTopic(topic.getId(), topic.getName(), topic.getStatus().name(),
                        topic.getDifficulty(), score));
            }
        }

        List<Assessment> mockTests = assessmentRepository.findBySubjectAndType(userId, subjectId,
                AssessmentType.MOCK_TEST);
        List<Assessment> latestMocks = mockTests.stream().limit(MOCK_TESTS_CONSIDERED).toList();

        Double syllabus = total == 0 ? null : ScoreMath.round2(100.0 * completed / total);
        Double masteryAvg = assessed == 0 ? null : ScoreMath.round2(masterySum / assessed);
        Double mock = latestMocks.isEmpty() ? null : ScoreMath.round2(latestMocks.stream()
                .mapToDouble(a -> a.getScorePercent().doubleValue()).average().orElse(0));
        Double revision = completed == 0 ? null : ScoreMath.round2(100.0 * revisedOnSchedule / completed);

        ReadinessCalculator.ReadinessInputs inputs = new ReadinessCalculator.ReadinessInputs(syllabus, masteryAvg,
                mock, revision);
        ReadinessCalculator.ReadinessResult result = calculator.calculate(inputs);

        Map<String, String> details = new HashMap<>();
        details.put("syllabusCompletion", total == 0 ? "No topics added for this subject yet"
                : "%d of %d topics completed".formatted(completed, total));
        details.put("topicMastery", assessed == 0 ? "No topic has been assessed yet"
                : "Average of %d assessed topic%s (%d not assessed)".formatted(assessed, assessed == 1 ? "" : "s",
                notAssessed));
        details.put("mockTestPerformance", latestMocks.isEmpty() ? "No mock tests recorded yet"
                : "Average of the latest %d mock test%s".formatted(latestMocks.size(),
                latestMocks.size() == 1 ? "" : "s"));
        details.put("revisionCompletion", completed == 0 ? "No completed topics to revise yet"
                : "%d of %d completed topics revised on schedule".formatted(revisedOnSchedule, completed));

        List<ReadinessResponse.FocusArea> focus = new ArrayList<>();
        focus.addAll(weak.stream().limit(5).toList());
        focus.addAll(needsImprovement.stream().limit(3).toList());
        if (overdueRevisions > 0) {
            focus.add(new ReadinessResponse.FocusArea("OVERDUE_REVISION", "Overdue revisions",
                    "%d topic%s overdue for revision.".formatted(overdueRevisions, overdueRevisions == 1 ? " is" : "s are"),
                    null));
        }
        int daysRemaining = (int) ChronoUnit.DAYS.between(today, exam.getExamDate());
        if (!pending.isEmpty()) {
            int pendingMinutes = topics.stream().filter(t -> t.getStatus() != TopicStatus.COMPLETED)
                    .mapToInt(Topic::getEstimatedMinutes).sum();
            String perDay = daysRemaining > 0
                    ? " (about %d min of study per day)".formatted((int) Math.ceil(pendingMinutes / (double) daysRemaining))
                    : "";
            focus.add(new ReadinessResponse.FocusArea("PENDING_TOPICS", "Syllabus not finished",
                    "%d topic%s still to cover%s.".formatted(pending.size(), pending.size() == 1 ? "" : "s", perDay),
                    null));
        }
        if (notAssessed > 0 && total > 0) {
            focus.add(new ReadinessResponse.FocusArea("NOT_ASSESSED", "Unmeasured topics",
                    "%d topic%s ha%s no quiz or test score yet - take a short quiz to measure mastery.".formatted(
                            notAssessed, notAssessed == 1 ? "" : "s", notAssessed == 1 ? "s" : "ve"), null));
        }
        if (latestMocks.isEmpty()) {
            focus.add(new ReadinessResponse.FocusArea("NO_MOCK_TEST", "No mock test yet",
                    "Attempt a full mock test under exam conditions to estimate your performance.", null));
        } else if (mock != null && mock < 60.0) {
            focus.add(new ReadinessResponse.FocusArea("LOW_MOCK_SCORE", "Low mock test scores",
                    "Mock test average is %.0f%% - practise timed papers and review mistakes.".formatted(mock), null));
        }
        return new Evaluation(inputs, result, details, pending, focus);
    }

    private void saveSnapshot(Long userId, Exam exam, LocalDate today, Evaluation evaluation) {
        ReadinessSnapshot snapshot = snapshotRepository.findForExamOnDate(exam.getId(), today)
                .orElseGet(() -> new ReadinessSnapshot(userId, exam, today));
        snapshot.apply(evaluation.result(), evaluation.inputs());
        snapshotRepository.save(snapshot);
    }

    private ReadinessResponse toResponse(Exam exam, LocalDate today, Evaluation evaluation,
                                         List<ReadinessResponse.TrendPoint> history) {
        List<ReadinessResponse.Component> components = evaluation.result().components().stream()
                .map(c -> new ReadinessResponse.Component(c.key(), c.label(), c.score(), c.weight(), c.available(),
                        evaluation.details().get(c.key())))
                .toList();
        return new ReadinessResponse(exam.getId(), exam.getTitle(), exam.getSubject().getId(),
                exam.getSubject().getName(), exam.getSubject().getColor(), exam.getExamDate(),
                (int) ChronoUnit.DAYS.between(today, exam.getExamDate()), evaluation.result().readinessScore(),
                evaluation.result().level(), evaluation.result().dataConfidence(),
                evaluation.result().dataSufficiency(), components, evaluation.pending(), evaluation.focus(), history,
                today, DISCLAIMER);
    }

    private LocalDate today(Long userId) {
        User user = userService.require(userId);
        return TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
    }

    private record Evaluation(ReadinessCalculator.ReadinessInputs inputs, ReadinessCalculator.ReadinessResult result,
                              Map<String, String> details, List<ReadinessResponse.PendingTopic> pending,
                              List<ReadinessResponse.FocusArea> focus) {
    }
}
