package com.smartstudy.revision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.smartstudy.revision.SpacedRepetitionScheduler.ReviewResult;
import com.smartstudy.revision.SpacedRepetitionScheduler.RevisionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpacedRepetitionSchedulerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 31);

    private final SpacedRepetitionScheduler scheduler = new SpacedRepetitionScheduler();

    @Test
    @DisplayName("GOOD, GOOD, GOOD, EASY gives intervals 1, 6, 15 and 52 days")
    void documentedSequence() {
        RevisionState state = RevisionState.initial();
        LocalDate date = DAY;

        ReviewResult first = scheduler.review(state, RevisionRating.GOOD, date);
        assertThat(first.intervalDays()).isEqualTo(1);
        assertThat(first.repetitions()).isEqualTo(1);
        assertThat(first.nextRevisionDate()).isEqualTo(DAY.plusDays(1));

        date = first.nextRevisionDate();
        ReviewResult second = scheduler.review(toState(first), RevisionRating.GOOD, date);
        assertThat(second.intervalDays()).isEqualTo(6);

        date = second.nextRevisionDate();
        ReviewResult third = scheduler.review(toState(second), RevisionRating.GOOD, date);
        assertThat(third.intervalDays()).isEqualTo(15);
        assertThat(third.easeFactor()).isEqualTo(2.50);

        ReviewResult fourth = scheduler.review(toState(third), RevisionRating.EASY, third.nextRevisionDate());
        assertThat(fourth.easeFactor()).isEqualTo(2.65);
        assertThat(fourth.intervalDays()).isEqualTo(52);
        assertThat(fourth.repetitions()).isEqualTo(4);
    }

    @Test
    void againRestartsTheCycleAndLowersEase() {
        ReviewResult result = scheduler.review(new RevisionState(2.5, 30, 5), RevisionRating.AGAIN, DAY);
        assertThat(result.repetitions()).isZero();
        assertThat(result.intervalDays()).isEqualTo(1);
        assertThat(result.easeFactor()).isEqualTo(2.30);
        assertThat(result.nextRevisionDate()).isEqualTo(DAY.plusDays(1));
    }

    @Test
    void hardGrowsTheIntervalSlowly() {
        ReviewResult result = scheduler.review(new RevisionState(2.5, 10, 3), RevisionRating.HARD, DAY);
        assertThat(result.intervalDays()).isEqualTo(12);
        assertThat(result.easeFactor()).isEqualTo(2.35);
        assertThat(result.repetitions()).isEqualTo(4);
    }

    @Test
    void easyOnANewTopicJumpsAhead() {
        ReviewResult result = scheduler.review(RevisionState.initial(), RevisionRating.EASY, DAY);
        assertThat(result.intervalDays()).isEqualTo(4);
        assertThat(result.easeFactor()).isEqualTo(2.65);
    }

    @Test
    void easeFactorNeverDropsBelowTheMinimum() {
        RevisionState state = new RevisionState(1.35, 3, 2);
        ReviewResult result = scheduler.review(state, RevisionRating.AGAIN, DAY);
        assertThat(result.easeFactor()).isEqualTo(SpacedRepetitionScheduler.MIN_EASE_FACTOR);
        result = scheduler.review(new RevisionState(result.easeFactor(), result.intervalDays(), result.repetitions()),
                RevisionRating.HARD, DAY);
        assertThat(result.easeFactor()).isEqualTo(SpacedRepetitionScheduler.MIN_EASE_FACTOR);
    }

    @Test
    void intervalIsCappedAtOneYear() {
        ReviewResult result = scheduler.review(new RevisionState(5.0, 300, 10), RevisionRating.EASY, DAY);
        assertThat(result.intervalDays()).isEqualTo(SpacedRepetitionScheduler.MAX_INTERVAL_DAYS);
    }

    @Test
    void successfulReviewNeverShortensTheInterval() {
        ReviewResult result = scheduler.review(new RevisionState(1.3, 2, 4), RevisionRating.GOOD, DAY);
        assertThat(result.intervalDays()).isGreaterThan(2);
    }

    @Test
    @DisplayName("a late review is scheduled from the day it actually happened")
    void lateReviewCountsFromTheReviewDate() {
        LocalDate lateReviewDate = LocalDate.of(2026, 11, 10);
        ReviewResult result = scheduler.review(new RevisionState(2.5, 6, 2), RevisionRating.GOOD, lateReviewDate);
        assertThat(result.nextRevisionDate()).isEqualTo(lateReviewDate.plusDays(15));
    }

    @Test
    @DisplayName("date arithmetic crosses month, year and leap-day boundaries correctly")
    void calendarBoundaries() {
        assertThat(scheduler.review(RevisionState.initial(), RevisionRating.GOOD, LocalDate.of(2026, 12, 31))
                .nextRevisionDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(scheduler.review(RevisionState.initial(), RevisionRating.GOOD, LocalDate.of(2028, 2, 28))
                .nextRevisionDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(scheduler.review(new RevisionState(2.5, 1, 1), RevisionRating.GOOD, LocalDate.of(2027, 2, 25))
                .nextRevisionDate()).isEqualTo(LocalDate.of(2027, 3, 3));
        assertThat(scheduler.firstReviewDate(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void explanationDescribesTheChange() {
        ReviewResult result = scheduler.review(new RevisionState(2.5, 6, 2), RevisionRating.GOOD, DAY);
        assertThat(result.explanation()).contains("GOOD").contains("6 -> 15").contains("2026-11-15");
    }

    private static RevisionState toState(ReviewResult result) {
        return new RevisionState(result.easeFactor(), result.intervalDays(), result.repetitions());
    }
}
