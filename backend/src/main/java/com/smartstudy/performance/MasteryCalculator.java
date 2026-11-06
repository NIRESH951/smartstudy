package com.smartstudy.performance;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.smartstudy.common.ScoreMath;

import org.springframework.stereotype.Component;

/**
 * Topic mastery formula.
 *
 * <pre>
 * Mastery = 0.60 x LatestAssessmentScore + 0.40 x HistoricalAverage
 * </pre>
 *
 * <ul>
 *   <li><b>Latest assessment score</b> - percentage of the most recent assessment of the topic
 *       (ordered by assessment date, then by insertion order).</li>
 *   <li><b>Historical average</b> - arithmetic mean of <em>all</em> recorded percentages of the
 *       topic, including the latest one. With a single assessment, mastery therefore equals that
 *       score; with more assessments, the latest result dominates but a single lucky or unlucky
 *       test cannot swing mastery completely.</li>
 * </ul>
 *
 * <p>Example: scores 40, 60, 80 (oldest to newest) give latest = 80, average = 60 and
 * mastery = 0.6 x 80 + 0.4 x 60 = 72 (GOOD).</p>
 *
 * <p>A topic without any score has <b>no</b> mastery ({@link Optional#empty()}): it is reported
 * as NOT_ASSESSED and is never treated as 0%.</p>
 */
@Component
public class MasteryCalculator {

    public static final double LATEST_WEIGHT = 0.60;
    public static final double HISTORICAL_WEIGHT = 0.40;

    public record MasteryResult(double masteryScore, double latestScore, double historicalAverage,
                                int assessmentCount, MasteryLevel level) {
    }

    /**
     * @param chronologicalScores topic percentages ordered from oldest to newest (0-100 each)
     */
    public Optional<MasteryResult> calculate(List<Double> chronologicalScores) {
        List<Double> scores = chronologicalScores == null
                ? List.of()
                : chronologicalScores.stream().filter(Objects::nonNull).map(ScoreMath::clamp).toList();
        if (scores.isEmpty()) {
            return Optional.empty();
        }
        double latest = scores.getLast();
        double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(latest);
        double mastery = ScoreMath.round2(ScoreMath.clamp(LATEST_WEIGHT * latest + HISTORICAL_WEIGHT * average));
        return Optional.of(new MasteryResult(mastery, ScoreMath.round2(latest), ScoreMath.round2(average),
                scores.size(), MasteryLevel.of(mastery)));
    }

    /** IMPROVING / DECLINING / STABLE by comparing two consecutive scores (2-point tolerance). */
    public static String trend(Double current, Double previous) {
        if (current == null || previous == null) {
            return "NEW";
        }
        double delta = current - previous;
        if (delta > 2.0) {
            return "IMPROVING";
        }
        if (delta < -2.0) {
            return "DECLINING";
        }
        return "STABLE";
    }
}
