package com.smartstudy.readiness;

import java.util.ArrayList;
import java.util.List;

import com.smartstudy.common.ScoreMath;

import org.springframework.stereotype.Component;

/**
 * Exam readiness formula.
 *
 * <pre>
 * Readiness = 0.30 x SyllabusCompletion + 0.30 x TopicMastery
 *           + 0.25 x MockTestPerformance + 0.15 x RevisionCompletion
 * </pre>
 *
 * <p><b>Insufficient data.</b> A component is "unavailable" when there is nothing to measure it
 * with (no assessed topics, no mock tests, no completed topics to revise). Unavailable components
 * are <em>not</em> counted as 0 - that would punish a student for missing data rather than for
 * poor preparation. Instead the score is computed from the available components with their
 * weights re-normalised to sum to 1, and the result carries a <b>data confidence</b> (the share
 * of the total weight that was available) plus a {@link DataSufficiency} flag:</p>
 *
 * <pre>
 * confidence = 100 x sum(weights of available components)
 * SUFFICIENT   : all four components available
 * PARTIAL      : confidence >= 55
 * INSUFFICIENT : confidence <  55   (score shown, but clearly marked as unreliable)
 * </pre>
 *
 * <p>Example: syllabus 80, mastery 70, no mock tests, revision 50 gives
 * (0.30x80 + 0.30x70 + 0.15x50) / 0.75 = 70.0 with 75 % confidence (PARTIAL).</p>
 *
 * <p>The result is an estimated preparation indicator, not a prediction of the actual marks.</p>
 */
@Component
public class ReadinessCalculator {

    public static final double SYLLABUS_WEIGHT = 0.30;
    public static final double MASTERY_WEIGHT = 0.30;
    public static final double MOCK_TEST_WEIGHT = 0.25;
    public static final double REVISION_WEIGHT = 0.15;
    public static final double PARTIAL_THRESHOLD = 0.55;

    /** Component scores (0-100) or null when unavailable. */
    public record ReadinessInputs(Double syllabusCompletion, Double topicMastery, Double mockTestPerformance,
                                  Double revisionCompletion) {
    }

    public record ComponentScore(String key, String label, Double score, double weight, boolean available) {
    }

    public record ReadinessResult(Double readinessScore, ReadinessLevel level, double dataConfidence,
                                  DataSufficiency dataSufficiency, List<ComponentScore> components) {
    }

    public ReadinessResult calculate(ReadinessInputs inputs) {
        List<ComponentScore> components = new ArrayList<>();
        components.add(component("syllabusCompletion", "Syllabus completion", inputs.syllabusCompletion(),
                SYLLABUS_WEIGHT));
        components.add(component("topicMastery", "Topic mastery", inputs.topicMastery(), MASTERY_WEIGHT));
        components.add(component("mockTestPerformance", "Mock test performance", inputs.mockTestPerformance(),
                MOCK_TEST_WEIGHT));
        components.add(component("revisionCompletion", "Revision completion", inputs.revisionCompletion(),
                REVISION_WEIGHT));

        double availableWeight = 0.0;
        double weightedSum = 0.0;
        for (ComponentScore component : components) {
            if (component.available()) {
                availableWeight += component.weight();
                weightedSum += component.weight() * component.score();
            }
        }

        if (availableWeight <= 0.0) {
            return new ReadinessResult(null, null, 0.0, DataSufficiency.INSUFFICIENT, components);
        }
        double score = ScoreMath.round2(ScoreMath.clamp(weightedSum / availableWeight));
        double confidence = ScoreMath.round2(availableWeight * 100.0);
        DataSufficiency sufficiency;
        if (availableWeight >= 0.999) {
            sufficiency = DataSufficiency.SUFFICIENT;
        } else if (availableWeight >= PARTIAL_THRESHOLD) {
            sufficiency = DataSufficiency.PARTIAL;
        } else {
            sufficiency = DataSufficiency.INSUFFICIENT;
        }
        return new ReadinessResult(score, ReadinessLevel.of(score), confidence, sufficiency, components);
    }

    private static ComponentScore component(String key, String label, Double score, double weight) {
        Double normalized = score == null ? null : ScoreMath.round2(ScoreMath.clamp(score));
        return new ComponentScore(key, label, normalized, weight, normalized != null);
    }
}
