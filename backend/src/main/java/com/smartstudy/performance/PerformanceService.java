package com.smartstudy.performance;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.smartstudy.assessment.AssessmentRepository;
import com.smartstudy.assessment.AssessmentTopicScore;
import com.smartstudy.assessment.AssessmentTopicScoreRepository;
import com.smartstudy.common.ScoreMath;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.subject.Subject;
import com.smartstudy.topic.Topic;
import com.smartstudy.topic.TopicRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mastery calculation, weak-topic detection and performance history. */
@Service
public class PerformanceService {

    private final AssessmentTopicScoreRepository scoreRepository;
    private final AssessmentRepository assessmentRepository;
    private final TopicMasteryRepository masteryRepository;
    private final TopicRepository topicRepository;
    private final MasteryCalculator calculator;

    public PerformanceService(AssessmentTopicScoreRepository scoreRepository,
                              AssessmentRepository assessmentRepository, TopicMasteryRepository masteryRepository,
                              TopicRepository topicRepository, MasteryCalculator calculator) {
        this.scoreRepository = scoreRepository;
        this.assessmentRepository = assessmentRepository;
        this.masteryRepository = masteryRepository;
        this.topicRepository = topicRepository;
        this.calculator = calculator;
    }

    /**
     * Recomputes the cached mastery of one topic from all of its scores. When the last score is
     * removed the mastery row is deleted, so the topic goes back to NOT_ASSESSED (never to 0 %).
     */
    @Transactional
    public void recomputeTopic(Long userId, Topic topic) {
        List<AssessmentTopicScore> history = scoreRepository.findHistory(userId, topic.getId());
        List<Double> scores = history.stream().map(score -> score.getScorePercent().doubleValue()).toList();
        Optional<MasteryCalculator.MasteryResult> result = calculator.calculate(scores);
        Optional<TopicMastery> existing = masteryRepository.findForTopic(topic.getId());
        if (result.isEmpty()) {
            existing.ifPresent(masteryRepository::delete);
            return;
        }
        TopicMastery mastery = existing.orElseGet(() -> new TopicMastery(userId, topic));
        mastery.apply(result.get(), history.getLast().getAssessment().getAssessedOn());
        masteryRepository.save(mastery);
    }

    @Transactional(readOnly = true)
    public WeakTopicResponse weakTopics(Long userId) {
        List<Topic> topics = topicRepository.findActiveWithSubject(userId);
        List<TopicMastery> masteries = masteryRepository.findAllWithTopic(userId).stream()
                .filter(m -> !m.getTopic().getSubject().isArchived())
                .toList();
        List<WeakTopicResponse.Item> items = masteries.stream()
                .filter(m -> m.getMasteryLevel() == MasteryLevel.WEAK
                        || m.getMasteryLevel() == MasteryLevel.NEEDS_IMPROVEMENT)
                .map(this::toWeakItem)
                .toList();
        int assessed = masteries.size();
        return new WeakTopicResponse(topics.size(), assessed, Math.max(0, topics.size() - assessed), items);
    }

    @Transactional(readOnly = true)
    public PerformanceSummaryResponse summary(Long userId) {
        List<Topic> topics = topicRepository.findAllWithSubject(userId);
        Map<Long, TopicMastery> mastery = new HashMap<>();
        for (TopicMastery item : masteryRepository.findAllForUser(userId)) {
            mastery.put(item.getTopic().getId(), item);
        }
        Map<Long, double[]> assessmentAverages = new HashMap<>();
        for (Object[] row : assessmentRepository.averageBySubject(userId)) {
            assessmentAverages.put((Long) row[0], new double[]{((Number) row[1]).doubleValue(),
                    ((Number) row[2]).doubleValue()});
        }

        Map<MasteryLevel, Integer> counts = new EnumMap<>(MasteryLevel.class);
        for (MasteryLevel level : MasteryLevel.values()) {
            counts.put(level, 0);
        }
        Map<Long, SubjectAccumulator> bySubject = new LinkedHashMap<>();
        double masterySum = 0;
        int masteryCount = 0;
        for (Topic topic : topics) {
            if (topic.getSubject().isArchived()) {
                continue;
            }
            TopicMastery m = mastery.get(topic.getId());
            Double score = m == null ? null : m.getMasteryScore().doubleValue();
            counts.merge(MasteryLevel.of(score), 1, Integer::sum);
            SubjectAccumulator acc = bySubject.computeIfAbsent(topic.getSubject().getId(),
                    id -> new SubjectAccumulator(topic.getSubject()));
            acc.totalTopics++;
            if (score != null) {
                acc.masterySum += score;
                acc.assessedTopics++;
                masterySum += score;
                masteryCount++;
                if (score < 40.0) {
                    acc.weakTopics++;
                }
            }
        }
        List<PerformanceSummaryResponse.SubjectPerformance> subjects = new ArrayList<>();
        for (SubjectAccumulator acc : bySubject.values()) {
            double[] avg = assessmentAverages.get(acc.subject.getId());
            subjects.add(new PerformanceSummaryResponse.SubjectPerformance(acc.subject.getId(), acc.subject.getName(),
                    acc.subject.getColor(),
                    acc.assessedTopics == 0 ? null : ScoreMath.round2(acc.masterySum / acc.assessedTopics),
                    acc.assessedTopics, acc.totalTopics, acc.weakTopics,
                    avg == null ? null : ScoreMath.round2(avg[0]), avg == null ? 0 : (int) avg[1]));
        }
        Map<String, Integer> levelCounts = new LinkedHashMap<>();
        counts.forEach((level, count) -> levelCounts.put(level.name(), count));
        return new PerformanceSummaryResponse(masteryCount == 0 ? null : ScoreMath.round2(masterySum / masteryCount),
                levelCounts, subjects);
    }

    @Transactional(readOnly = true)
    public TopicHistoryResponse topicHistory(Long userId, Long topicId) {
        Topic topic = topicRepository.findOwnedWithSubject(topicId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", topicId));
        List<AssessmentTopicScore> history = scoreRepository.findHistory(userId, topicId);
        List<TopicHistoryResponse.Point> points = new ArrayList<>();
        List<Double> running = new ArrayList<>();
        for (AssessmentTopicScore score : history) {
            running.add(score.getScorePercent().doubleValue());
            double masteryAfter = calculator.calculate(running).map(MasteryCalculator.MasteryResult::masteryScore)
                    .orElse(0.0);
            points.add(new TopicHistoryResponse.Point(score.getAssessment().getId(), score.getAssessment().getTitle(),
                    score.getAssessment().getAssessmentType(), score.getAssessment().getAssessedOn(),
                    score.getScorePercent().doubleValue(), masteryAfter));
        }
        Double current = masteryRepository.findForTopic(topicId).map(m -> m.getMasteryScore().doubleValue())
                .orElse(null);
        return new TopicHistoryResponse(topic.getId(), topic.getName(), topic.getSubject().getName(), current,
                MasteryLevel.of(current), points);
    }

    private WeakTopicResponse.Item toWeakItem(TopicMastery m) {
        Topic topic = m.getTopic();
        Double previous = ScoreMath.toDouble(m.getPreviousMasteryScore());
        double score = m.getMasteryScore().doubleValue();
        String trend = MasteryCalculator.trend(score, previous);
        return new WeakTopicResponse.Item(topic.getId(), topic.getName(), topic.getSubject().getId(),
                topic.getSubject().getName(), topic.getSubject().getColor(), score, m.getMasteryLevel(),
                m.getLatestScore().doubleValue(), m.getHistoricalAverage().doubleValue(), previous, trend,
                m.getAssessmentCount(), m.getLastAssessedOn(), topic.getStatus().name(),
                recommendation(topic, m.getMasteryLevel(), trend));
    }

    static String recommendation(Topic topic, MasteryLevel level, String trend) {
        StringBuilder text = new StringBuilder();
        if (level == MasteryLevel.WEAK) {
            text.append("Re-study the fundamentals of ").append(topic.getName())
                    .append(", work through solved examples, then take a short quiz to measure progress.");
        } else {
            text.append("Practise more problems on ").append(topic.getName())
                    .append(" and review your mistakes from the last assessment.");
        }
        if ("DECLINING".equals(trend)) {
            text.append(" Your scores are dropping - revise this topic before your next test.");
        } else if ("IMPROVING".equals(trend)) {
            text.append(" You are improving - keep the momentum going.");
        }
        text.append(" The planner automatically gives this topic higher priority.");
        return text.toString();
    }

    private static final class SubjectAccumulator {
        private final Subject subject;
        private int totalTopics;
        private int assessedTopics;
        private int weakTopics;
        private double masterySum;

        private SubjectAccumulator(Subject subject) {
            this.subject = subject;
        }
    }
}
