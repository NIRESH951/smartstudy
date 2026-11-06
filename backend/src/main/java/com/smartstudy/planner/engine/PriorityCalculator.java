package com.smartstudy.planner.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.smartstudy.common.ScoreMath;
import com.smartstudy.config.SmartStudyProperties;

import org.springframework.stereotype.Component;

/**
 * Weighted priority of a topic - the heart of the Adaptive Study Planning Engine.
 *
 * <pre>
 * Priority = (0.30 x Weakness) + (0.25 x Exam Urgency) + (0.20 x Difficulty)
 *          + (0.15 x Revision Overdue) + (0.10 x Subject Importance)
 * </pre>
 *
 * <p>The weights are configurable ({@code smartstudy.planner.weights.*}); the weighted sum is
 * divided by the sum of the weights, so the priority always stays on the 0-100 scale even if
 * the configured weights do not add up to exactly 1.</p>
 *
 * <p>Every factor is first normalised to 0-100:</p>
 * <ul>
 *   <li><b>Weakness</b> = 100 - mastery. A topic that was never assessed gets a neutral value
 *       (default 50) instead of 100, because "no data" is not the same as "0 % mastery".</li>
 *   <li><b>Exam urgency</b> = 100 x (H - d) / H for d days until the nearest exam and horizon
 *       H (default 30 days): 100 on exam day, 50 at 15 days, 0 at 30+ days or with no exam.</li>
 *   <li><b>Difficulty</b> = (difficulty - 1) x 25, mapping 1..5 to 0, 25, 50, 75, 100.</li>
 *   <li><b>Revision overdue</b> = 0 when nothing is due; 20 when due today, rising linearly to
 *       100 when the revision is C or more days overdue (cap C, default 14):
 *       20 + 80 x min(overdueDays, C) / C.</li>
 *   <li><b>Subject importance</b> = (importance - 1) x 25.</li>
 * </ul>
 *
 * <p>Worked example (default weights): mastery 30 % (W = 70), exam in 6 days (U = 80),
 * difficulty 4 (D = 75), revision 7 days overdue (R = 60), importance 5 (I = 100):
 * 0.30x70 + 0.25x80 + 0.20x75 + 0.15x60 + 0.10x100 = 21 + 20 + 15 + 9 + 10 = <b>75.0</b>.</p>
 */
@Component
public class PriorityCalculator {

    private final SmartStudyProperties.Planner config;

    public PriorityCalculator(SmartStudyProperties properties) {
        this.config = properties.getPlanner();
    }

    public PriorityBreakdown calculate(PriorityInput input) {
        SmartStudyProperties.Weights w = config.getWeights();
        double totalWeight = w.getWeakness() + w.getExamUrgency() + w.getDifficulty() + w.getRevisionOverdue()
                + w.getSubjectImportance();
        if (totalWeight <= 0) {
            throw new IllegalStateException("Planner weights must add up to a positive number");
        }

        double weakness = weaknessScore(input.masteryScore());
        double urgency = examUrgencyScore(input.daysUntilExam());
        double difficulty = levelScore(input.difficulty());
        double overdue = revisionOverdueScore(input.daysOverdue());
        double importance = levelScore(input.subjectImportance());

        double priority = (w.getWeakness() * weakness
                + w.getExamUrgency() * urgency
                + w.getDifficulty() * difficulty
                + w.getRevisionOverdue() * overdue
                + w.getSubjectImportance() * importance) / totalWeight;

        List<Reason> reasons = new ArrayList<>();
        Double mastery = input.masteryScore();
        if (mastery == null) {
            reasons.add(new Reason(w.getWeakness() * weakness, "Not assessed yet (neutral weakness assumed)"));
        } else if (mastery < 40) {
            reasons.add(new Reason(w.getWeakness() * weakness, "Weak topic (mastery %.0f%%)".formatted(mastery)));
        } else if (mastery < 65) {
            reasons.add(new Reason(w.getWeakness() * weakness,
                    "Needs improvement (mastery %.0f%%)".formatted(mastery)));
        }
        Integer days = input.daysUntilExam();
        if (days != null && days >= 0 && urgency > 0) {
            String text = switch (days) {
                case 0 -> "Exam today";
                case 1 -> "Exam tomorrow";
                default -> "Exam in %d days".formatted(days);
            };
            reasons.add(new Reason(w.getExamUrgency() * urgency, text));
        }
        if (input.difficulty() >= 4) {
            reasons.add(new Reason(w.getDifficulty() * difficulty,
                    "%s topic (difficulty %d/5)".formatted(input.difficulty() == 5 ? "Very hard" : "Hard",
                            input.difficulty())));
        }
        Integer overdueDays = input.daysOverdue();
        if (overdueDays != null && overdueDays >= 0) {
            String text = overdueDays == 0
                    ? "Revision due today"
                    : "Revision overdue by %d day%s".formatted(overdueDays, overdueDays == 1 ? "" : "s");
            reasons.add(new Reason(w.getRevisionOverdue() * overdue, text));
        }
        if (input.subjectImportance() >= 4) {
            reasons.add(new Reason(w.getSubjectImportance() * importance, "High-importance subject"));
        }
        List<String> ordered = reasons.stream()
                .sorted(Comparator.comparingDouble(Reason::contribution).reversed())
                .map(Reason::text)
                .toList();

        return new PriorityBreakdown(
                ScoreMath.round2(weakness),
                ScoreMath.round2(urgency),
                ScoreMath.round2(difficulty),
                ScoreMath.round2(overdue),
                ScoreMath.round2(importance),
                ScoreMath.round2(ScoreMath.clamp(priority)),
                ordered);
    }

    /** 100 - mastery; unassessed topics get the configured neutral value. */
    public double weaknessScore(Double masteryScore) {
        if (masteryScore == null) {
            return ScoreMath.clamp(config.getUnassessedWeakness());
        }
        return ScoreMath.clamp(100.0 - masteryScore);
    }

    /** Linear ramp from 0 (horizon or further away) to 100 (exam today). */
    public double examUrgencyScore(Integer daysUntilExam) {
        if (daysUntilExam == null || daysUntilExam < 0) {
            return 0.0;
        }
        int horizon = Math.max(1, config.getUrgencyHorizonDays());
        if (daysUntilExam >= horizon) {
            return 0.0;
        }
        return ScoreMath.clamp(100.0 * (horizon - daysUntilExam) / horizon);
    }

    /** Maps a 1..5 scale to 0..100. */
    public double levelScore(int level) {
        int bounded = Math.max(1, Math.min(5, level));
        return (bounded - 1) * 25.0;
    }

    /** 0 when not due, 20 when due today, linearly up to 100 at the cap. */
    public double revisionOverdueScore(Integer daysOverdue) {
        if (daysOverdue == null || daysOverdue < 0) {
            return 0.0;
        }
        int cap = Math.max(1, config.getRevisionOverdueCapDays());
        return ScoreMath.clamp(20.0 + 80.0 * Math.min(daysOverdue, cap) / cap);
    }

    private record Reason(double contribution, String text) {
    }
}
