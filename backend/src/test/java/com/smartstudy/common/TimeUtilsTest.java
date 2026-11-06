package com.smartstudy.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.smartstudy.common.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeUtilsTest {

    @Test
    @DisplayName("'today' is the student's local date, not the UTC date")
    void todayUsesTheStudentsTimeZone() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-10T20:00:00Z"), ZoneOffset.UTC);
        assertThat(TimeUtils.today(clock, ZoneId.of("UTC"))).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(TimeUtils.today(clock, ZoneId.of("Asia/Kolkata"))).isEqualTo(LocalDate.of(2026, 3, 11));
        assertThat(TimeUtils.today(clock, ZoneId.of("America/Los_Angeles"))).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void localWallClockTimeIsConvertedToUtc() {
        Instant instant = TimeUtils.toInstant(LocalDate.of(2026, 11, 2), LocalTime.of(18, 0), ZoneId.of("Asia/Kolkata"));
        assertThat(instant).isEqualTo(Instant.parse("2026-11-02T12:30:00Z"));
        assertThat(TimeUtils.localDate(instant, ZoneId.of("Asia/Kolkata"))).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    @DisplayName("a wall-clock time inside a DST gap is shifted forward instead of failing")
    void daylightSavingGap() {
        // 2026-03-08 02:30 does not exist in New York (clocks jump from 02:00 to 03:00).
        Instant instant = TimeUtils.toInstant(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30),
                ZoneId.of("America/New_York"));
        assertThat(instant).isEqualTo(Instant.parse("2026-03-08T07:30:00Z"));
    }

    @Test
    void unknownZonesFallBackToUtcButAreRejectedInRequests() {
        assertThat(TimeUtils.zone("Not/AZone")).isEqualTo(ZoneId.of("UTC"));
        assertThat(TimeUtils.requireValidZone("Asia/Kolkata")).isEqualTo("Asia/Kolkata");
        assertThatThrownBy(() -> TimeUtils.requireValidZone("Mars/Base")).isInstanceOf(BadRequestException.class);
    }
}
