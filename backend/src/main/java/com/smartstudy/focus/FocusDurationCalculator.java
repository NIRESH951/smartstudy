package com.smartstudy.focus;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * Server-side focus timing.
 *
 * <pre>
 * elapsedFocus = (effectiveEnd - startedAt) - totalPausedSeconds
 *   effectiveEnd = pausedAt while the session is paused (time stops), otherwise "now"
 *
 * actualFocus  = min(elapsedFocus, plannedMinutes x 60, clientReportedSeconds if supplied)
 * </pre>
 *
 * <p>The browser may report how long it counted, but that value can only <em>lower</em> the
 * recorded duration (for example when the student stopped early). It can never raise it above
 * what the server measured, so manipulating the browser timer gains nothing.</p>
 */
@Component
public class FocusDurationCalculator {

    /** Focus seconds elapsed so far, excluding pauses. Never negative. */
    public long elapsedFocusSeconds(Instant startedAt, Instant now, int totalPausedSeconds, Instant pausedAt) {
        Instant effectiveEnd = pausedAt != null ? pausedAt : now;
        if (effectiveEnd.isBefore(startedAt)) {
            return 0;
        }
        long gross = Duration.between(startedAt, effectiveEnd).getSeconds();
        return Math.max(0, gross - Math.max(0, totalPausedSeconds));
    }

    /** Validated duration to store when the session is completed. */
    public int actualFocusSeconds(Instant startedAt, Instant now, int totalPausedSeconds, Instant pausedAt,
                                  int plannedMinutes, Integer clientReportedSeconds) {
        long measured = elapsedFocusSeconds(startedAt, now, totalPausedSeconds, pausedAt);
        long value = Math.min(measured, plannedMinutes * 60L);
        if (clientReportedSeconds != null) {
            value = Math.min(value, Math.max(0, clientReportedSeconds));
        }
        return (int) value;
    }

    /** Seconds left until the planned focus length is reached. */
    public long remainingSeconds(long elapsedFocusSeconds, int plannedMinutes) {
        return Math.max(0, plannedMinutes * 60L - elapsedFocusSeconds);
    }
}
