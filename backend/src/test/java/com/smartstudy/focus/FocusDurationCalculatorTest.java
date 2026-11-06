package com.smartstudy.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FocusDurationCalculatorTest {

    private static final Instant START = Instant.parse("2026-10-31T06:00:00Z");

    private final FocusDurationCalculator calculator = new FocusDurationCalculator();

    @Test
    void elapsedTimeOfARunningSession() {
        assertThat(calculator.elapsedFocusSeconds(START, START.plusSeconds(600), 0, null)).isEqualTo(600);
    }

    @Test
    void pausesAreExcluded() {
        // 20 minutes since start, 5 minutes of completed pauses
        assertThat(calculator.elapsedFocusSeconds(START, START.plusSeconds(1200), 300, null)).isEqualTo(900);
    }

    @Test
    @DisplayName("time stops while a session is paused")
    void pausedSessionDoesNotAccumulateTime() {
        Instant pausedAt = START.plusSeconds(400);
        assertThat(calculator.elapsedFocusSeconds(START, START.plusSeconds(4000), 0, pausedAt)).isEqualTo(400);
    }

    @Test
    @DisplayName("a session left running for hours is capped at its planned length")
    void actualDurationIsCappedAtPlannedLength() {
        int actual = calculator.actualFocusSeconds(START, START.plusSeconds(3 * 3600), 0, null, 25, null);
        assertThat(actual).isEqualTo(25 * 60);
    }

    @Test
    @DisplayName("the browser cannot claim more focus time than the server measured")
    void clientCannotInflateDuration() {
        int actual = calculator.actualFocusSeconds(START, START.plusSeconds(600), 0, null, 25, 1500);
        assertThat(actual).isEqualTo(600);
    }

    @Test
    void clientMayReportLessTime() {
        int actual = calculator.actualFocusSeconds(START, START.plusSeconds(1500), 0, null, 25, 1200);
        assertThat(actual).isEqualTo(1200);
    }

    @Test
    void clockGoingBackwardsNeverGivesNegativeTime() {
        assertThat(calculator.elapsedFocusSeconds(START, START.minusSeconds(30), 0, null)).isZero();
        assertThat(calculator.actualFocusSeconds(START, START.plusSeconds(60), 0, null, 25, -50)).isZero();
    }

    @Test
    void remainingSeconds() {
        assertThat(calculator.remainingSeconds(300, 25)).isEqualTo(1200);
        assertThat(calculator.remainingSeconds(2000, 25)).isZero();
    }
}
