package com.smartstudy.planner.engine;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.smartstudy.config.SmartStudyProperties;
import com.smartstudy.planner.SessionType;

import org.springframework.stereotype.Component;

/**
 * Builds a conflict-free timetable from availability windows and prioritised tasks.
 *
 * <h2>Algorithm (greedy, priority-driven, constraint-satisfying)</h2>
 * <ol>
 *   <li><b>Free slots.</b> For every day of the plan, each availability window is converted from
 *       the student's local time to UTC instants (DST-safe), trimmed so nothing starts in the past,
 *       and the time occupied by preserved sessions (completed, in-progress, locked; padded by the
 *       break length) is subtracted. The remaining free intervals are cut into slots of the
 *       preferred session length separated by breaks. Slots shorter than the minimum slot length are
 *       discarded. Because preserved time is subtracted first, <b>generated sessions can never
 *       overlap existing ones</b>.</li>
 *   <li><b>Assignment.</b> Slots are processed in chronological order. For each slot every eligible
 *       task gets an effective score
 *       <pre>score = priority x decay^(sessions already given to the task)  [x 0.90 if same subject as previous slot]</pre>
 *       The decay (default 0.80) spreads time over several topics instead of letting one topic take
 *       every slot, while high-priority topics still receive more sessions. The 0.90 factor
 *       interleaves subjects, which improves retention. A task is eligible when it still has remaining
 *       minutes, the slot date is on/after its earliest date (e.g. a revision's due date), the session
 *       would end before the subject's exam (deadline) and the topic has not reached its per-day
 *       session limit. The best task gets {@code min(slotLength, max(remaining, minSlot))} minutes.</li>
 *   <li><b>Limits.</b> Daily and weekly (Monday-Sunday) study limits, including minutes already used by
 *       preserved sessions, are never exceeded.</li>
 *   <li><b>Reuse.</b> If a short task (e.g. a 30-minute revision) leaves enough of the slot free,
 *       the remainder becomes a new slot.</li>
 *   <li><b>Transparency.</b> Work that does not fit is returned as "unscheduled" with a reason.</li>
 * </ol>
 * The algorithm is deterministic: equal inputs always give the same timetable.
 */
@Component
public class TimetableGenerator {

    /** Score multiplier applied when a task has the same subject as the previous session of the day. */
    public static final double INTERLEAVING_FACTOR = 0.90;
    /** Generated sessions start at least this many minutes after "now", on a 5-minute boundary. */
    public static final int LEAD_MINUTES = 5;

    private final SmartStudyProperties.Planner config;

    public TimetableGenerator(SmartStudyProperties properties) {
        this.config = properties.getPlanner();
    }

    public TimetableResult generate(TimetableRequest request) {
        int minSlot = Math.max(10, config.getMinSlotMinutes());
        int sessionMinutes = Math.max(minSlot, request.sessionMinutes());
        int breakMinutes = Math.max(0, request.breakMinutes());
        double decay = Math.max(0.05, Math.min(1.0, config.getRepeatDecayFactor()));
        int maxPerDay = Math.max(1, config.getMaxSessionsPerTopicPerDay());

        List<FreeSlot> slots = buildFreeSlots(request, minSlot, sessionMinutes, breakMinutes);

        List<PlanningTask> tasks = new ArrayList<>(request.tasks());
        tasks.sort(Comparator.comparingDouble(PlanningTask::priority).reversed()
                .thenComparing(task -> task.deadline() == null ? Instant.MAX : task.deadline())
                .thenComparing(task -> task.sessionType() == SessionType.STUDY ? 1 : 0)
                .thenComparingLong(PlanningTask::topicId));

        Map<LocalDate, Integer> usedByDay = new HashMap<>(request.usedMinutesByDay());
        Map<LocalDate, Integer> usedByWeek = new HashMap<>(request.usedMinutesByWeek());
        Map<String, Integer> assignedCount = new HashMap<>();
        Map<String, Integer> topicSessionsPerDay = new HashMap<>();
        List<TimetableResult.PlannedSlot> planned = new ArrayList<>();

        LocalDate currentDate = null;
        Long previousSubjectId = null;

        for (int i = 0; i < slots.size(); i++) {
            FreeSlot slot = slots.get(i);
            if (!slot.date().equals(currentDate)) {
                currentDate = slot.date();
                previousSubjectId = null;
            }
            LocalDate week = weekStart(slot.date());
            long dayLeft = (long) request.dailyCapMinutes() - usedByDay.getOrDefault(slot.date(), 0);
            long weekLeft = (long) request.weeklyCapMinutes() - usedByWeek.getOrDefault(week, 0);
            int available = (int) Math.min(slot.minutes(), Math.min(dayLeft, weekLeft));
            if (available < minSlot) {
                continue;
            }

            PlanningTask best = null;
            double bestScore = -1.0;
            int bestLength = 0;
            for (PlanningTask task : tasks) {
                if (task.remainingMinutes() <= 0 || slot.date().isBefore(task.earliestDate())) {
                    continue;
                }
                String dayKey = slot.date() + "|" + task.topicId();
                if (topicSessionsPerDay.getOrDefault(dayKey, 0) >= maxPerDay) {
                    continue;
                }
                int length = Math.min(available, Math.max(task.remainingMinutes(), minSlot));
                Instant end = slot.start().plusSeconds(length * 60L);
                if (task.deadline() != null && end.isAfter(task.deadline())) {
                    continue;
                }
                double score = task.priority() * Math.pow(decay, assignedCount.getOrDefault(task.key(), 0));
                if (previousSubjectId != null && previousSubjectId == task.subjectId()) {
                    score *= INTERLEAVING_FACTOR;
                }
                if (score > bestScore + 1e-9) {
                    best = task;
                    bestScore = score;
                    bestLength = length;
                }
            }
            if (best == null) {
                continue;
            }

            Instant end = slot.start().plusSeconds(bestLength * 60L);
            planned.add(new TimetableResult.PlannedSlot(best, slot.date(), slot.start(), end));
            best.consume(bestLength);
            assignedCount.merge(best.key(), 1, Integer::sum);
            topicSessionsPerDay.merge(slot.date() + "|" + best.topicId(), 1, Integer::sum);
            usedByDay.merge(slot.date(), bestLength, Integer::sum);
            usedByWeek.merge(week, bestLength, Integer::sum);
            previousSubjectId = best.subjectId();

            Instant nextStart = end.plusSeconds(breakMinutes * 60L);
            if (nextStart.isBefore(slot.end()) && Duration.between(nextStart, slot.end()).toMinutes() >= minSlot) {
                slots.add(i + 1, new FreeSlot(slot.date(), nextStart, slot.end()));
            }
        }

        List<TimetableResult.UnscheduledWork> unscheduled = tasks.stream()
                .filter(task -> task.remainingMinutes() > 0)
                .map(task -> new TimetableResult.UnscheduledWork(task.topicId(), task.subjectId(),
                        task.sessionType(), task.remainingMinutes(), unscheduledReason(task)))
                .toList();
        return new TimetableResult(planned, unscheduled);
    }

    /** Monday of the week containing the date (weeks run Monday-Sunday, ISO-8601). */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    List<FreeSlot> buildFreeSlots(TimetableRequest request, int minSlot, int sessionMinutes, int breakMinutes) {
        Instant earliest = roundUpToFiveMinutes(request.now().plusSeconds(LEAD_MINUTES * 60L));
        List<TimeInterval> busy = request.busy().stream()
                .map(interval -> breakMinutes == 0 ? interval : new TimeInterval(
                        interval.start().minusSeconds(breakMinutes * 60L),
                        interval.end().plusSeconds(breakMinutes * 60L)))
                .sorted(Comparator.comparing(TimeInterval::start))
                .toList();

        List<FreeSlot> result = new ArrayList<>();
        for (LocalDate date = request.startDate(); !date.isAfter(request.endDate()); date = date.plusDays(1)) {
            DayOfWeek day = date.getDayOfWeek();
            List<AvailabilityWindow> windows = request.windows().stream()
                    .filter(window -> window.dayOfWeek() == day)
                    .sorted(Comparator.comparing(AvailabilityWindow::start))
                    .toList();
            for (AvailabilityWindow window : windows) {
                Instant windowStart = ZonedDateTime.of(date, window.start(), request.zone()).toInstant();
                Instant windowEnd = ZonedDateTime.of(date, window.end(), request.zone()).toInstant();
                if (!windowEnd.isAfter(earliest)) {
                    continue;
                }
                if (windowStart.isBefore(earliest)) {
                    windowStart = earliest;
                }
                if (!windowEnd.isAfter(windowStart)) {
                    continue;
                }
                for (TimeInterval free : subtract(new TimeInterval(windowStart, windowEnd), busy)) {
                    Instant cursor = free.start();
                    while (cursor.isBefore(free.end()) && Duration.between(cursor, free.end()).toMinutes() >= minSlot) {
                        long length = Math.min(sessionMinutes, Duration.between(cursor, free.end()).toMinutes());
                        Instant slotEnd = cursor.plusSeconds(length * 60L);
                        result.add(new FreeSlot(date, cursor, slotEnd));
                        cursor = slotEnd.plusSeconds(breakMinutes * 60L);
                    }
                }
            }
        }
        result.sort(Comparator.comparing(FreeSlot::start));
        return result;
    }

    /** Removes the busy intervals (sorted by start) from the given interval. */
    static List<TimeInterval> subtract(TimeInterval interval, List<TimeInterval> busy) {
        List<TimeInterval> free = new ArrayList<>();
        Instant cursor = interval.start();
        for (TimeInterval taken : busy) {
            if (!taken.end().isAfter(cursor)) {
                continue;
            }
            if (!taken.start().isBefore(interval.end())) {
                break;
            }
            if (taken.start().isAfter(cursor)) {
                free.add(new TimeInterval(cursor, taken.start()));
            }
            cursor = taken.end();
            if (!cursor.isBefore(interval.end())) {
                break;
            }
        }
        if (cursor.isBefore(interval.end())) {
            free.add(new TimeInterval(cursor, interval.end()));
        }
        return free;
    }

    static Instant roundUpToFiveMinutes(Instant instant) {
        long seconds = instant.getEpochSecond() + (instant.getNano() > 0 ? 1 : 0);
        long step = 300;
        long rounded = Math.floorDiv(seconds + step - 1, step) * step;
        return Instant.ofEpochSecond(rounded);
    }

    private static String unscheduledReason(PlanningTask task) {
        if (task.deadline() != null) {
            return "Not enough free time before the exam. Add availability or raise your daily/weekly limit.";
        }
        return "Did not fit into this planning period; it will be planned in the next timetable.";
    }

    /** A free block of time on a local date. */
    record FreeSlot(LocalDate date, Instant start, Instant end) {

        long minutes() {
            return Duration.between(start, end).toMinutes();
        }
    }
}
