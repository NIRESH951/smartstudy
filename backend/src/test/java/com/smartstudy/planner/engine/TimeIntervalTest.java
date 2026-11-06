package com.smartstudy.planner.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Session conflict detection rules (half-open intervals). */
class TimeIntervalTest {

    private static final Instant T10 = Instant.parse("2030-01-07T10:00:00Z");
    private static final Instant T11 = Instant.parse("2030-01-07T11:00:00Z");
    private static final Instant T1030 = Instant.parse("2030-01-07T10:30:00Z");
    private static final Instant T1130 = Instant.parse("2030-01-07T11:30:00Z");
    private static final Instant T12 = Instant.parse("2030-01-07T12:00:00Z");

    @Test
    void partialOverlapIsAConflict() {
        assertThat(TimeInterval.overlaps(T10, T11, T1030, T1130)).isTrue();
        assertThat(TimeInterval.overlaps(T1030, T1130, T10, T11)).isTrue();
    }

    @Test
    void containedIntervalIsAConflict() {
        assertThat(TimeInterval.overlaps(T10, T12, T1030, T11)).isTrue();
        assertThat(new TimeInterval(T1030, T11).overlaps(new TimeInterval(T10, T12))).isTrue();
    }

    @Test
    void identicalIntervalsConflict() {
        assertThat(TimeInterval.overlaps(T10, T11, T10, T11)).isTrue();
    }

    @Test
    @DisplayName("back-to-back sessions (one ends when the next starts) do not conflict")
    void touchingIntervalsDoNotConflict() {
        assertThat(TimeInterval.overlaps(T10, T11, T11, T12)).isFalse();
        assertThat(TimeInterval.overlaps(T11, T12, T10, T11)).isFalse();
    }

    @Test
    void separateIntervalsDoNotConflict() {
        assertThat(TimeInterval.overlaps(T10, T1030, T11, T12)).isFalse();
    }

    @Test
    void anIntervalMustEndAfterItStarts() {
        assertThatThrownBy(() -> new TimeInterval(T11, T10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeInterval(T10, T10)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new TimeInterval(T10, T1130).minutes()).isEqualTo(90);
    }
}
