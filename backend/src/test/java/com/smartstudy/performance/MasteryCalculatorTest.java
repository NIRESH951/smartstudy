package com.smartstudy.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MasteryCalculatorTest {

    private final MasteryCalculator calculator = new MasteryCalculator();

    @Test
    @DisplayName("a topic without assessments has no mastery at all (not 0 %)")
    void unassessedTopicHasNoMastery() {
        assertThat(calculator.calculate(List.of())).isEmpty();
        assertThat(calculator.calculate(null)).isEmpty();
        assertThat(MasteryLevel.of(null)).isEqualTo(MasteryLevel.NOT_ASSESSED);
    }

    @Test
    void singleAssessmentGivesThatScore() {
        MasteryCalculator.MasteryResult result = calculator.calculate(List.of(55.0)).orElseThrow();
        assertThat(result.masteryScore()).isEqualTo(55.0);
        assertThat(result.latestScore()).isEqualTo(55.0);
        assertThat(result.historicalAverage()).isEqualTo(55.0);
        assertThat(result.assessmentCount()).isEqualTo(1);
        assertThat(result.level()).isEqualTo(MasteryLevel.NEEDS_IMPROVEMENT);
    }

    @Test
    @DisplayName("Mastery = 0.6 x latest + 0.4 x historical average (40, 60, 80 -> 72)")
    void weightedFormula() {
        MasteryCalculator.MasteryResult result = calculator.calculate(List.of(40.0, 60.0, 80.0)).orElseThrow();
        assertThat(result.latestScore()).isEqualTo(80.0);
        assertThat(result.historicalAverage()).isEqualTo(60.0);
        assertThat(result.masteryScore()).isCloseTo(72.0, within(0.001));
        assertThat(result.level()).isEqualTo(MasteryLevel.GOOD);
    }

    @Test
    void latestScoreDominatesButOneBadTestDoesNotErasePreviousWork() {
        Optional<MasteryCalculator.MasteryResult> result = calculator.calculate(List.of(90.0, 90.0, 30.0));
        // 0.6 x 30 + 0.4 x 70 = 46
        assertThat(result).isPresent();
        assertThat(result.get().masteryScore()).isCloseTo(46.0, within(0.001));
        assertThat(result.get().level()).isEqualTo(MasteryLevel.NEEDS_IMPROVEMENT);
    }

    @Test
    void scoresOutsideTheRangeAreClamped() {
        List<Double> scores = new ArrayList<>();
        scores.add(120.0);
        scores.add(null);
        scores.add(-5.0);
        MasteryCalculator.MasteryResult result = calculator.calculate(scores).orElseThrow();
        assertThat(result.assessmentCount()).isEqualTo(2);
        assertThat(result.masteryScore()).isCloseTo(0.6 * 0 + 0.4 * 50, within(0.001));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "0, WEAK", "39.99, WEAK",
            "40, NEEDS_IMPROVEMENT", "64.99, NEEDS_IMPROVEMENT",
            "65, GOOD", "79.99, GOOD",
            "80, STRONG", "100, STRONG"})
    void weakTopicClassification(double score, MasteryLevel expected) {
        assertThat(MasteryLevel.of(score)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} vs {1} -> {2}")
    @CsvSource({"70, 60, IMPROVING", "50, 60, DECLINING", "61, 60, STABLE"})
    void trendDetection(double current, double previous, String expected) {
        assertThat(MasteryCalculator.trend(current, previous)).isEqualTo(expected);
    }

    @Test
    void trendOfFirstAssessmentIsNew() {
        assertThat(MasteryCalculator.trend(50.0, null)).isEqualTo("NEW");
    }
}
