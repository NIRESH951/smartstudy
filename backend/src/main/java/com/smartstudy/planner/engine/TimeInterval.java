package com.smartstudy.planner.engine;

import java.time.Duration;
import java.time.Instant;

/** Half-open time interval [start, end). */
public record TimeInterval(Instant start, Instant end) {

    public TimeInterval {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Interval end must be after start");
        }
    }

    public long minutes() {
        return Duration.between(start, end).toMinutes();
    }

    /**
     * Two half-open intervals overlap when each one starts before the other ends.
     * Touching intervals (one ends exactly when the next starts) do <b>not</b> overlap.
     */
    public boolean overlaps(TimeInterval other) {
        return overlaps(start, end, other.start, other.end);
    }

    public static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }
}
