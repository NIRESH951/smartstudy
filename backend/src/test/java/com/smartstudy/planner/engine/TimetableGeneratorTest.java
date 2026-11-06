package com.smartstudy.planner.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.smartstudy.config.SmartStudyProperties;
import com.smartstudy.planner.SessionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimetableGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /** Monday. */
    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);
    /** 05:30 in Kolkata on Monday - well before the evening study window. */
    private static final Instant EARLY_MONDAY = ZonedDateTime.of(MONDAY, LocalTime.of(5, 30), ZONE).toInstant();

    private final TimetableGenerator generator = new TimetableGenerator(new SmartStudyProperties());

    @Test
    @DisplayName("generated sessions never overlap and stay inside availability windows and limits")
    void noOverlapsWithinWindowsAndLimits() {
        TimetableResult result = generator.generate(request(
                List.of(task(1, 10, SessionType.STUDY, 5000, 80), task(2, 20, SessionType.STUDY, 5000, 70)),
                List.of(), 180, 900, Map.of()));

        List<TimetableResult.PlannedSlot> slots = result.slots();
        assertThat(slots).isNotEmpty();
        assertNoOverlap(slots);
        assertThat(result.totalMinutes()).isLessThanOrEqualTo(900);
        Map<LocalDate, Integer> perDay = new HashMap<>();
        for (TimetableResult.PlannedSlot slot : slots) {
            perDay.merge(slot.date(), slot.minutes(), Integer::sum);
            LocalTime start = slot.start().atZone(ZONE).toLocalTime();
            LocalTime end = slot.end().atZone(ZONE).toLocalTime();
            assertThat(start).isAfterOrEqualTo(LocalTime.of(18, 0));
            assertThat(end).isBeforeOrEqualTo(LocalTime.of(21, 0));
        }
        assertThat(perDay.values()).allMatch(minutes -> minutes <= 180);
        // 3 slots of 50 minutes per evening, limited to 900 minutes per week -> 18 sessions.
        assertThat(slots).hasSize(18);
    }

    @Test
    @DisplayName("time taken by preserved (completed/locked) sessions is never double-booked")
    void preservedSessionsAreAvoided() {
        Instant busyStart = at(MONDAY, 19, 0);
        Instant busyEnd = at(MONDAY, 19, 50);
        TimetableResult result = generator.generate(request(
                List.of(task(1, 10, SessionType.STUDY, 5000, 80)),
                List.of(new TimeInterval(busyStart, busyEnd)), 180, 900, Map.of()));

        List<TimetableResult.PlannedSlot> monday = result.slots().stream()
                .filter(slot -> slot.date().equals(MONDAY)).toList();
        assertThat(monday).allMatch(slot -> !TimeInterval.overlaps(slot.start(), slot.end(), busyStart, busyEnd));
        assertThat(monday).extracting(slot -> slot.start().atZone(ZONE).toLocalTime())
                .containsExactly(LocalTime.of(18, 0), LocalTime.of(20, 0));
    }

    @Test
    void dailyLimitIsRespected() {
        TimetableResult result = generator.generate(request(
                List.of(task(1, 10, SessionType.STUDY, 5000, 80), task(2, 20, SessionType.STUDY, 5000, 60)),
                List.of(), 100, 10_000, Map.of()));

        Map<LocalDate, Integer> perDay = new HashMap<>();
        result.slots().forEach(slot -> perDay.merge(slot.date(), slot.minutes(), Integer::sum));
        assertThat(perDay.values()).allMatch(minutes -> minutes <= 100);
        assertThat(perDay.get(MONDAY)).isEqualTo(100);
    }

    @Test
    void weeklyLimitIncludesMinutesAlreadyUsed() {
        Map<LocalDate, Integer> usedByWeek = Map.of(MONDAY, 850);
        TimetableResult result = generator.generate(request(
                List.of(task(1, 10, SessionType.STUDY, 5000, 80)), List.of(), 180, 900, usedByWeek));

        assertThat(result.totalMinutes()).isEqualTo(50);
        assertThat(result.slots()).hasSize(1);
    }

    @Test
    @DisplayName("the highest-priority topic gets the first slot and decay spreads time across topics")
    void priorityOrderAndDecay() {
        TimetableResult result = generator.generate(request(
                List.of(task(1, 10, SessionType.STUDY, 5000, 60), task(2, 20, SessionType.STUDY, 5000, 55)),
                List.of(), 180, 900, Map.of()));

        assertThat(result.slots().get(0).task().topicId()).isEqualTo(1);
        // 60 x 0.8 = 48 < 55, so the second slot goes to topic 2.
        assertThat(result.slots().get(1).task().topicId()).isEqualTo(2);
        long topic1 = result.slots().stream().filter(s -> s.task().topicId() == 1).count();
        long topic2 = result.slots().stream().filter(s -> s.task().topicId() == 2).count();
        assertThat(topic1).isGreaterThanOrEqualTo(topic2);
        assertThat(topic2).isPositive();
    }

    @Test
    void revisionIsNotScheduledBeforeItsDueDate() {
        LocalDate due = MONDAY.plusDays(2);
        PlanningTask revision = new PlanningTask(5, 50, SessionType.REVISION, 30, due, null, 99, "review");
        TimetableResult result = generator.generate(request(List.of(revision), List.of(), 180, 900, Map.of()));

        assertThat(result.slots()).hasSize(1);
        assertThat(result.slots().get(0).date()).isEqualTo(due);
        assertThat(result.slots().get(0).minutes()).isEqualTo(30);
    }

    @Test
    void studyEndsBeforeTheExamDeadline() {
        Instant examStart = at(MONDAY.plusDays(1), 9, 0);
        PlanningTask task = new PlanningTask(1, 10, SessionType.STUDY, 5000, MONDAY, examStart, 90, "exam");
        TimetableResult result = generator.generate(request(List.of(task), List.of(), 180, 900, Map.of()));

        assertThat(result.slots()).isNotEmpty();
        assertThat(result.slots()).allMatch(slot -> !slot.end().isAfter(examStart));
        assertThat(result.unscheduled()).hasSize(1);
        assertThat(result.unscheduled().get(0).reason()).contains("before the exam");
    }

    @Test
    void nothingIsScheduledInThePast() {
        Instant now = at(MONDAY, 18, 30);
        TimetableRequest base = request(List.of(task(1, 10, SessionType.STUDY, 5000, 80)), List.of(), 180, 900,
                Map.of());
        TimetableRequest request = new TimetableRequest(base.startDate(), base.endDate(), ZONE, now, base.windows(),
                base.busy(), base.usedMinutesByDay(), base.usedMinutesByWeek(), 180, 900, 50, 10, base.tasks());

        TimetableResult result = generator.generate(request);
        assertThat(result.slots()).allMatch(slot -> slot.start().isAfter(now));
        // now + 5 minutes lead time, rounded up to a 5-minute boundary
        assertThat(result.slots().get(0).start()).isEqualTo(at(MONDAY, 18, 35));
    }

    @Test
    void oneTopicGetsAtMostTwoSessionsPerDay() {
        TimetableResult result = generator.generate(request(
                List.of(task(1, 10, SessionType.STUDY, 5000, 80)), List.of(), 180, 900, Map.of()));

        Map<LocalDate, Integer> perDay = new HashMap<>();
        result.slots().forEach(slot -> perDay.merge(slot.date(), 1, Integer::sum));
        assertThat(perDay.values()).allMatch(count -> count <= 2);
    }

    @Test
    void workThatDoesNotFitIsReportedAsUnscheduled() {
        TimetableResult result = generator.generate(request(
                List.of(task(1, 10, SessionType.STUDY, 5000, 80)), List.of(), 180, 900, Map.of()));

        assertThat(result.unscheduled()).hasSize(1);
        assertThat(result.unscheduled().get(0).remainingMinutes()).isEqualTo(5000 - result.totalMinutes());
    }

    @Test
    @DisplayName("a short revision leaves the rest of a long slot for other work")
    void remainderOfASlotIsReused() {
        PlanningTask revision = new PlanningTask(5, 50, SessionType.REVISION, 30, MONDAY, null, 95, "review");
        PlanningTask study = task(1, 10, SessionType.STUDY, 500, 60);
        TimetableRequest base = request(List.of(revision, study), List.of(), 180, 900, Map.of());
        TimetableRequest request = new TimetableRequest(MONDAY, MONDAY, ZONE, EARLY_MONDAY, base.windows(), List.of(),
                Map.of(), Map.of(), 180, 900, 90, 10, List.of(revision, study));

        List<TimetableResult.PlannedSlot> slots = generator.generate(request).slots();
        assertThat(slots.get(0).task().sessionType()).isEqualTo(SessionType.REVISION);
        assertThat(slots.get(0).end()).isEqualTo(at(MONDAY, 18, 30));
        assertThat(slots.get(1).start()).isEqualTo(at(MONDAY, 18, 40));
        assertNoOverlap(slots);
    }

    @Test
    void sameInputGivesTheSameTimetable() {
        List<Instant> first = generator.generate(request(List.of(task(1, 10, SessionType.STUDY, 900, 70),
                task(2, 20, SessionType.STUDY, 900, 70)), List.of(), 180, 900, Map.of())).slots().stream()
                .map(TimetableResult.PlannedSlot::start).toList();
        List<Instant> second = generator.generate(request(List.of(task(1, 10, SessionType.STUDY, 900, 70),
                task(2, 20, SessionType.STUDY, 900, 70)), List.of(), 180, 900, Map.of())).slots().stream()
                .map(TimetableResult.PlannedSlot::start).toList();
        assertThat(first).isEqualTo(second);
    }

    // ------------------------------------------------------------------ helpers

    private static TimetableRequest request(List<PlanningTask> tasks, List<TimeInterval> busy, int dailyCap,
                                            int weeklyCap, Map<LocalDate, Integer> usedByWeek) {
        List<AvailabilityWindow> windows = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            windows.add(new AvailabilityWindow(day, LocalTime.of(18, 0), LocalTime.of(21, 0)));
        }
        return new TimetableRequest(MONDAY, MONDAY.plusDays(6), ZONE, EARLY_MONDAY, windows, busy, Map.of(),
                usedByWeek, dailyCap, weeklyCap, 50, 10, tasks);
    }

    private static PlanningTask task(long topicId, long subjectId, SessionType type, int minutes, double priority) {
        return new PlanningTask(topicId, subjectId, type, minutes, MONDAY, null, priority, "test");
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ZONE).toInstant();
    }

    private static void assertNoOverlap(List<TimetableResult.PlannedSlot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                TimetableResult.PlannedSlot a = slots.get(i);
                TimetableResult.PlannedSlot b = slots.get(j);
                assertThat(TimeInterval.overlaps(a.start(), a.end(), b.start(), b.end()))
                        .as("slots %s and %s overlap", a, b).isFalse();
            }
        }
    }
}
