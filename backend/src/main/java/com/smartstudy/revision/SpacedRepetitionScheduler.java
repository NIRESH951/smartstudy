package com.smartstudy.revision;

import java.time.LocalDate;
import java.util.Locale;

import com.smartstudy.common.ScoreMath;

import org.springframework.stereotype.Component;

/**
 * Simplified SM-2 spaced-repetition algorithm.
 *
 * <p>Each topic keeps three numbers: the <b>ease factor</b> EF (how easy the topic is to
 * remember, 1.30 - 5.00, starts at 2.50), the current <b>interval</b> I in days and the number of
 * consecutive successful <b>repetitions</b> n. After a review the student picks a rating:</p>
 *
 * <pre>
 * Rating | EF change | new n | new interval
 * -------+-----------+-------+---------------------------------------------------------
 * AGAIN  |   -0.20   |   0   | 1 day (the topic restarts the cycle)
 * HARD   |   -0.15   |  n+1  | n = 0: 1 day,  otherwise max(1, round(I x 1.2))
 * GOOD   |    0.00   |  n+1  | n = 0: 1 day,  n = 1: 6 days,  otherwise round(I x EF')
 * EASY   |   +0.15   |  n+1  | n = 0: 4 days, n = 1: 8 days,  otherwise round(I x EF' x 1.3)
 * </pre>
 *
 * <p>EF' is the updated ease factor, clamped to [1.30, 5.00] and rounded to two decimals (the
 * precision stored in the database). Intervals are clamped to [1, 365] days, and GOOD / EASY
 * always grow the interval by at least one day so that a successful review never shortens it.</p>
 *
 * <p><b>Date boundaries.</b> The next date is {@code reviewDate + interval}, where reviewDate is
 * the student's <em>local</em> calendar date at the moment of the review (computed with the
 * student's time zone, not UTC). A late review is therefore scheduled from the day it actually
 * happened, not from its original due date.</p>
 *
 * <p>Example: a new topic rated GOOD, GOOD, GOOD, EASY on time gets intervals 1, 6, 15 and
 * round(15 x 2.65 x 1.3) = 52 days.</p>
 */
@Component
public class SpacedRepetitionScheduler {

    public static final double DEFAULT_EASE_FACTOR = 2.50;
    public static final double MIN_EASE_FACTOR = 1.30;
    public static final double MAX_EASE_FACTOR = 5.00;
    public static final int MAX_INTERVAL_DAYS = 365;
    /** A topic enters the revision cycle with its first review due the day after it is completed. */
    public static final int FIRST_REVIEW_DELAY_DAYS = 1;

    public record RevisionState(double easeFactor, int intervalDays, int repetitions) {

        public static RevisionState initial() {
            return new RevisionState(DEFAULT_EASE_FACTOR, 0, 0);
        }
    }

    public record ReviewResult(double easeFactor, int intervalDays, int repetitions, LocalDate nextRevisionDate,
                               String explanation) {
    }

    public ReviewResult review(RevisionState state, RevisionRating rating, LocalDate reviewDate) {
        if (state == null) {
            state = RevisionState.initial();
        }
        double easeFactor = clampEase(state.easeFactor());
        int previousInterval = Math.max(0, state.intervalDays());
        int repetitions = Math.max(0, state.repetitions());
        int base = Math.max(1, previousInterval);

        double newEase;
        int newRepetitions;
        int interval;
        switch (rating) {
            case AGAIN -> {
                newEase = clampEase(easeFactor - 0.20);
                newRepetitions = 0;
                interval = 1;
            }
            case HARD -> {
                newEase = clampEase(easeFactor - 0.15);
                newRepetitions = repetitions + 1;
                interval = repetitions == 0 ? 1 : Math.max(1, (int) Math.round(base * 1.2));
            }
            case GOOD -> {
                newEase = easeFactor;
                newRepetitions = repetitions + 1;
                if (repetitions == 0) {
                    interval = 1;
                } else if (repetitions == 1) {
                    interval = 6;
                } else {
                    interval = Math.max(base + 1, (int) Math.round(base * newEase));
                }
            }
            case EASY -> {
                newEase = clampEase(easeFactor + 0.15);
                newRepetitions = repetitions + 1;
                if (repetitions == 0) {
                    interval = 4;
                } else if (repetitions == 1) {
                    interval = 8;
                } else {
                    interval = Math.max(base + 1, (int) Math.round(base * newEase * 1.3));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported rating " + rating);
        }
        interval = Math.min(MAX_INTERVAL_DAYS, Math.max(1, interval));
        LocalDate next = reviewDate.plusDays(interval);
        String explanation = String.format(Locale.ROOT,
                "Rated %s: interval %d -> %d day%s, ease factor %.2f -> %.2f. Next revision on %s.",
                rating, previousInterval, interval, interval == 1 ? "" : "s", easeFactor, newEase, next);
        return new ReviewResult(newEase, interval, newRepetitions, next, explanation);
    }

    /** First review date for a topic that has just been completed. */
    public LocalDate firstReviewDate(LocalDate completionDate) {
        return completionDate.plusDays(FIRST_REVIEW_DELAY_DAYS);
    }

    private static double clampEase(double value) {
        return ScoreMath.round2(Math.max(MIN_EASE_FACTOR, Math.min(MAX_EASE_FACTOR, value)));
    }
}
