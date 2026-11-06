package com.smartstudy.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartstudy.readiness.ReadinessCalculator.ReadinessInputs;
import com.smartstudy.readiness.ReadinessCalculator.ReadinessResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadinessCalculatorTest {

    private final ReadinessCalculator calculator = new ReadinessCalculator();

    @Test
    @DisplayName("Readiness = 0.30 S + 0.30 M + 0.25 T + 0.15 R when all data is available")
    void fullFormula() {
        ReadinessResult result = calculator.calculate(new ReadinessInputs(80.0, 70.0, 60.0, 50.0));
        // 24 + 21 + 15 + 7.5 = 67.5
        assertThat(result.readinessScore()).isCloseTo(67.5, within(0.001));
        assertThat(result.dataConfidence()).isEqualTo(100.0);
        assertThat(result.dataSufficiency()).isEqualTo(DataSufficiency.SUFFICIENT);
        assertThat(result.level()).isEqualTo(ReadinessLevel.ON_TRACK);
        assertThat(result.components()).hasSize(4).allMatch(ReadinessCalculator.ComponentScore::available);
    }

    @Test
    @DisplayName("missing data is not counted as zero: weights are re-normalised and confidence drops")
    void missingComponentIsRenormalised() {
        ReadinessResult result = calculator.calculate(new ReadinessInputs(80.0, 70.0, null, 50.0));
        // (24 + 21 + 7.5) / 0.75 = 70
        assertThat(result.readinessScore()).isCloseTo(70.0, within(0.001));
        assertThat(result.dataConfidence()).isEqualTo(75.0);
        assertThat(result.dataSufficiency()).isEqualTo(DataSufficiency.PARTIAL);
        assertThat(result.components()).filteredOn(c -> !c.available())
                .extracting(ReadinessCalculator.ComponentScore::key).containsExactly("mockTestPerformance");
    }

    @Test
    void onlySyllabusDataIsInsufficient() {
        ReadinessResult result = calculator.calculate(new ReadinessInputs(40.0, null, null, null));
        assertThat(result.readinessScore()).isEqualTo(40.0);
        assertThat(result.dataConfidence()).isEqualTo(30.0);
        assertThat(result.dataSufficiency()).isEqualTo(DataSufficiency.INSUFFICIENT);
    }

    @Test
    void noDataGivesNoScore() {
        ReadinessResult result = calculator.calculate(new ReadinessInputs(null, null, null, null));
        assertThat(result.readinessScore()).isNull();
        assertThat(result.level()).isNull();
        assertThat(result.dataConfidence()).isZero();
        assertThat(result.dataSufficiency()).isEqualTo(DataSufficiency.INSUFFICIENT);
    }

    @Test
    void scoresAreClampedTo0To100() {
        ReadinessResult result = calculator.calculate(new ReadinessInputs(150.0, -10.0, 100.0, 100.0));
        assertThat(result.readinessScore()).isBetween(0.0, 100.0);
    }

    @Test
    void readinessLevels() {
        assertThat(ReadinessLevel.of(85.0)).isEqualTo(ReadinessLevel.WELL_PREPARED);
        assertThat(ReadinessLevel.of(80.0)).isEqualTo(ReadinessLevel.WELL_PREPARED);
        assertThat(ReadinessLevel.of(60.0)).isEqualTo(ReadinessLevel.ON_TRACK);
        assertThat(ReadinessLevel.of(59.99)).isEqualTo(ReadinessLevel.NEEDS_WORK);
        assertThat(ReadinessLevel.of(39.99)).isEqualTo(ReadinessLevel.AT_RISK);
        assertThat(ReadinessLevel.of(null)).isNull();
    }
}
