package com.smartstudy.planner.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartstudy.config.SmartStudyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriorityCalculatorTest {

    private SmartStudyProperties properties;
    private PriorityCalculator calculator;

    @BeforeEach
    void setUp() {
        properties = new SmartStudyProperties();
        calculator = new PriorityCalculator(properties);
    }

    @Test
    @DisplayName("worked example from the documentation gives 75.0")
    void workedExample() {
        // mastery 30 -> W 70, exam in 6 days -> U 80, difficulty 4 -> D 75,
        // 7 days overdue -> R 60, importance 5 -> I 100
        PriorityBreakdown result = calculator.calculate(new PriorityInput(30.0, 6, 4, 7, 5));

        assertThat(result.weakness()).isEqualTo(70.0);
        assertThat(result.examUrgency()).isEqualTo(80.0);
        assertThat(result.difficulty()).isEqualTo(75.0);
        assertThat(result.revisionOverdue()).isEqualTo(60.0);
        assertThat(result.subjectImportance()).isEqualTo(100.0);
        assertThat(result.priority()).isCloseTo(75.0, within(0.001));
    }

    @Test
    @DisplayName("an unassessed topic gets the neutral weakness, never the 100 of a 0% mastery")
    void unassessedTopicIsNotTreatedAsZeroMastery() {
        assertThat(calculator.weaknessScore(null)).isEqualTo(50.0);
        assertThat(calculator.weaknessScore(0.0)).isEqualTo(100.0);

        PriorityBreakdown unassessed = calculator.calculate(new PriorityInput(null, null, 3, null, 3));
        PriorityBreakdown zeroMastery = calculator.calculate(new PriorityInput(0.0, null, 3, null, 3));
        assertThat(unassessed.priority()).isLessThan(zeroMastery.priority());
        assertThat(unassessed.reasons()).anyMatch(reason -> reason.startsWith("Not assessed yet"));
    }

    @ParameterizedTest(name = "{0} days until exam -> urgency {1}")
    @CsvSource({"0, 100", "1, 96.67", "15, 50", "29, 3.33", "30, 0", "45, 0"})
    void examUrgencyIsLinearOverTheHorizon(int days, double expected) {
        assertThat(calculator.examUrgencyScore(days)).isCloseTo(expected, within(0.01));
    }

    @Test
    void noExamOrPastExamHasNoUrgency() {
        assertThat(calculator.examUrgencyScore(null)).isZero();
        assertThat(calculator.examUrgencyScore(-1)).isZero();
    }

    @ParameterizedTest(name = "level {0} -> {1}")
    @CsvSource({"1, 0", "2, 25", "3, 50", "4, 75", "5, 100", "0, 0", "9, 100"})
    void levelsAreNormalisedTo0To100(int level, double expected) {
        assertThat(calculator.levelScore(level)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} days overdue -> {1}")
    @CsvSource({"0, 20", "7, 60", "14, 100", "40, 100"})
    void revisionOverdueScoreRampsUpToTheCap(int daysOverdue, double expected) {
        assertThat(calculator.revisionOverdueScore(daysOverdue)).isCloseTo(expected, within(0.001));
    }

    @Test
    void nothingDueMeansNoOverdueScore() {
        assertThat(calculator.revisionOverdueScore(null)).isZero();
        assertThat(calculator.revisionOverdueScore(-3)).isZero();
    }

    @Test
    @DisplayName("weights are configurable and normalised by their sum")
    void weightsAreConfigurable() {
        PriorityInput input = new PriorityInput(20.0, null, 1, null, 1);
        double defaultPriority = calculator.calculate(input).priority();
        assertThat(defaultPriority).isCloseTo(0.30 * 80, within(0.001));

        // Only weakness counts -> priority equals the weakness score.
        SmartStudyProperties.Weights weights = properties.getPlanner().getWeights();
        weights.setWeakness(2.0);
        weights.setExamUrgency(0);
        weights.setDifficulty(0);
        weights.setRevisionOverdue(0);
        weights.setSubjectImportance(0);
        assertThat(new PriorityCalculator(properties).calculate(input).priority()).isCloseTo(80.0, within(0.001));
    }

    @Test
    void priorityAlwaysStaysWithin0And100() {
        PriorityBreakdown max = calculator.calculate(new PriorityInput(0.0, 0, 5, 30, 5));
        PriorityBreakdown min = calculator.calculate(new PriorityInput(100.0, null, 1, null, 1));
        assertThat(max.priority()).isEqualTo(100.0);
        assertThat(min.priority()).isZero();
    }

    @Test
    @DisplayName("reasons explain the strongest factors first")
    void reasonsAreOrderedByContribution() {
        PriorityBreakdown result = calculator.calculate(new PriorityInput(10.0, 2, 5, null, 2));
        assertThat(result.reasons().getFirst()).startsWith("Weak topic");
        assertThat(result.reasons()).contains("Exam in 2 days", "Very hard topic (difficulty 5/5)");
        assertThat(result.summary()).contains("Weak topic (mastery 10%)");
    }
}
