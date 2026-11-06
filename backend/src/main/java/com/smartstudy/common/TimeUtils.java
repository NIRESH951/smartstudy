package com.smartstudy.common;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.smartstudy.common.exception.BadRequestException;

/**
 * Time-zone helpers.
 *
 * <p>All instants are stored in UTC. Whenever the application needs a calendar date
 * ("today", "the day of this session") it converts the instant into the student's own
 * time zone first. Example: 2026-03-10T20:00Z is already 2026-03-11 01:30 in Asia/Kolkata,
 * so a revision completed at that moment counts for 11 March, not 10 March.</p>
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    public static ZoneId zone(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException | NullPointerException ex) {
            return ZoneId.of("UTC");
        }
    }

    /** Validates a time-zone id coming from a request. */
    public static String requireValidZone(String timeZone) {
        try {
            return ZoneId.of(timeZone).getId();
        } catch (DateTimeException | NullPointerException ex) {
            throw new BadRequestException("timeZone", "Unknown time zone: " + timeZone);
        }
    }

    public static LocalDate today(Clock clock, ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    public static LocalDate localDate(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate();
    }

    /** First instant of the given local day (DST safe). */
    public static Instant startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** Converts a local date + wall-clock time to an instant (DST gaps are shifted forward). */
    public static Instant toInstant(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }
}
