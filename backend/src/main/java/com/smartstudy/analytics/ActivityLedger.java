package com.smartstudy.analytics;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.smartstudy.common.TimeUtils;
import com.smartstudy.focus.FocusSession;
import com.smartstudy.focus.FocusStatus;
import com.smartstudy.planner.SessionStatus;
import com.smartstudy.planner.StudySession;
import com.smartstudy.revision.RevisionLog;

/**
 * Aggregates raw study records into per-day and per-subject numbers in the student's time zone.
 *
 * <p>To avoid double counting, "actual study minutes" are the minutes of completed/incomplete
 * study sessions plus focus sessions and revisions that were <em>not</em> linked to a study
 * session (a linked focus block or revision is already part of its session's minutes).</p>
 */
final class ActivityLedger {

    static final class DayStats {
        int planned;
        int actual;
        int focus;
        int completed;
        int incomplete;
        int missed;
    }

    static final class SubjectStats {
        final Long subjectId;
        final String name;
        final String color;
        int planned;
        int actual;

        SubjectStats(Long subjectId, String name, String color) {
            this.subjectId = subjectId;
            this.name = name;
            this.color = color;
        }
    }

    private final ZoneId zone;
    private final Map<LocalDate, DayStats> days = new TreeMap<>();
    private final Map<Long, SubjectStats> subjects = new LinkedHashMap<>();

    ActivityLedger(ZoneId zone) {
        this.zone = zone;
    }

    void addSession(StudySession session) {
        if (session.getStatus() == SessionStatus.CANCELLED) {
            return;
        }
        DayStats day = day(TimeUtils.localDate(session.getStartAt(), zone));
        SubjectStats subject = subjects.computeIfAbsent(session.getSubject().getId(),
                id -> new SubjectStats(id, session.getSubject().getName(), session.getSubject().getColor()));
        day.planned += session.getPlannedMinutes();
        subject.planned += session.getPlannedMinutes();
        if (SessionStatus.DONE.contains(session.getStatus())) {
            int minutes = session.getActualMinutes() == null ? 0 : session.getActualMinutes();
            day.actual += minutes;
            subject.actual += minutes;
            if (session.getStatus() == SessionStatus.COMPLETED) {
                day.completed++;
            } else {
                day.incomplete++;
            }
        } else if (session.getStatus() == SessionStatus.MISSED) {
            day.missed++;
        }
    }

    void addFocus(FocusSession focus) {
        if (focus.getStatus() != FocusStatus.COMPLETED || focus.getActualFocusSeconds() == null) {
            return;
        }
        int minutes = focus.getActualFocusSeconds() / 60;
        DayStats day = day(TimeUtils.localDate(focus.getStartedAt(), zone));
        day.focus += minutes;
        if (focus.getStudySession() == null) {
            day.actual += minutes;
            if (focus.getSubject() != null) {
                SubjectStats subject = subjects.computeIfAbsent(focus.getSubject().getId(),
                        id -> new SubjectStats(id, focus.getSubject().getName(), focus.getSubject().getColor()));
                subject.actual += minutes;
            }
        }
    }

    void addRevision(RevisionLog log) {
        if (log.getStudySession() != null || log.getDurationMinutes() == null) {
            return;
        }
        day(log.getRevisedOn()).actual += log.getDurationMinutes();
    }

    DayStats day(LocalDate date) {
        return days.computeIfAbsent(date, d -> new DayStats());
    }

    DayStats peek(LocalDate date) {
        DayStats stats = days.get(date);
        return stats == null ? new DayStats() : stats;
    }

    Map<Long, SubjectStats> subjects() {
        return subjects;
    }

    /** Consecutive days with study, ending today (or yesterday when nothing has been done today yet). */
    int currentStreak(LocalDate today, LocalDate earliest) {
        LocalDate cursor = peek(today).actual > 0 ? today : today.minusDays(1);
        int streak = 0;
        while (!cursor.isBefore(earliest) && peek(cursor).actual > 0) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    int longestStreak(LocalDate from, LocalDate to) {
        int best = 0;
        int run = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (peek(date).actual > 0) {
                run++;
                best = Math.max(best, run);
            } else {
                run = 0;
            }
        }
        return best;
    }

    int activeDays(LocalDate from, LocalDate to) {
        int active = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (peek(date).actual > 0) {
                active++;
            }
        }
        return active;
    }
}
