package com.smartstudy.analytics;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.smartstudy.assessment.Assessment;
import com.smartstudy.assessment.AssessmentRepository;
import com.smartstudy.common.ScoreMath;
import com.smartstudy.common.TimeUtils;
import com.smartstudy.focus.FocusSession;
import com.smartstudy.focus.FocusSessionRepository;
import com.smartstudy.focus.FocusStatus;
import com.smartstudy.notification.NotificationService;
import com.smartstudy.performance.MasteryLevel;
import com.smartstudy.performance.PerformanceService;
import com.smartstudy.performance.PerformanceSummaryResponse;
import com.smartstudy.performance.TopicMasteryRepository;
import com.smartstudy.planner.PlannerService;
import com.smartstudy.planner.SessionStatus;
import com.smartstudy.planner.StudySession;
import com.smartstudy.planner.StudySessionRepository;
import com.smartstudy.planner.engine.TimetableGenerator;
import com.smartstudy.readiness.ReadinessService;
import com.smartstudy.readiness.ReadinessSnapshot;
import com.smartstudy.readiness.ReadinessSnapshotRepository;
import com.smartstudy.revision.RevisionDueResponse;
import com.smartstudy.revision.RevisionLog;
import com.smartstudy.revision.RevisionLogRepository;
import com.smartstudy.revision.RevisionService;
import com.smartstudy.subject.SubjectResponse;
import com.smartstudy.subject.SubjectService;
import com.smartstudy.topic.TopicRepository;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Study analytics: dashboard overview, weekly report, subject progress and productivity trends. */
@Service
public class AnalyticsService {

    private static final int STREAK_LOOKBACK_DAYS = 120;
    private static final int CONSISTENCY_DAYS = 14;

    private final UserService userService;
    private final StudySessionRepository sessionRepository;
    private final FocusSessionRepository focusRepository;
    private final RevisionLogRepository revisionLogRepository;
    private final AssessmentRepository assessmentRepository;
    private final TopicRepository topicRepository;
    private final TopicMasteryRepository masteryRepository;
    private final PlannerService plannerService;
    private final ReadinessService readinessService;
    private final RevisionService revisionService;
    private final SubjectService subjectService;
    private final PerformanceService performanceService;
    private final NotificationService notificationService;
    private final ReadinessSnapshotRepository snapshotRepository;
    private final Clock clock;

    public AnalyticsService(UserService userService, StudySessionRepository sessionRepository,
                            FocusSessionRepository focusRepository, RevisionLogRepository revisionLogRepository,
                            AssessmentRepository assessmentRepository, TopicRepository topicRepository,
                            TopicMasteryRepository masteryRepository, PlannerService plannerService,
                            ReadinessService readinessService, RevisionService revisionService,
                            SubjectService subjectService, PerformanceService performanceService,
                            NotificationService notificationService, ReadinessSnapshotRepository snapshotRepository,
                            Clock clock) {
        this.userService = userService;
        this.sessionRepository = sessionRepository;
        this.focusRepository = focusRepository;
        this.revisionLogRepository = revisionLogRepository;
        this.assessmentRepository = assessmentRepository;
        this.topicRepository = topicRepository;
        this.masteryRepository = masteryRepository;
        this.plannerService = plannerService;
        this.readinessService = readinessService;
        this.revisionService = revisionService;
        this.subjectService = subjectService;
        this.performanceService = performanceService;
        this.notificationService = notificationService;
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
    }

    @Transactional
    public DashboardOverviewResponse overview(Long userId) {
        plannerService.detectMissedAndReschedule(userId);
        User user = userService.require(userId);
        ZoneId zone = TimeUtils.zone(user.getTimeZone());
        LocalDate today = TimeUtils.today(clock, zone);
        LocalDate from = today.minusDays(STREAK_LOOKBACK_DAYS);
        Loaded loaded = load(userId, zone, from, today);
        ActivityLedger ledger = loaded.ledger();

        ActivityLedger.DayStats todayStats = ledger.peek(today);
        List<StudySession> todaySessions = loaded.sessions().stream()
                .filter(s -> TimeUtils.localDate(s.getStartAt(), zone).equals(today))
                .toList();
        int completedToday = (int) todaySessions.stream().filter(s -> SessionStatus.DONE.contains(s.getStatus()))
                .count();
        int missedToday = (int) todaySessions.stream().filter(s -> s.getStatus() == SessionStatus.MISSED).count();
        double percent = todayStats.planned == 0 ? 0.0
                : ScoreMath.round2(Math.min(100.0, 100.0 * todayStats.actual / todayStats.planned));
        DashboardOverviewResponse.TodaySummary todaySummary = new DashboardOverviewResponse.TodaySummary(
                todayStats.planned, todayStats.actual, percent, todaySessions.size(), completedToday, missedToday);

        LocalDate weekStart = TimetableGenerator.weekStart(today);
        int weekPlanned = 0;
        int weekActual = 0;
        int weekFocus = 0;
        int weekCompleted = 0;
        int weekMissed = 0;
        for (LocalDate date = weekStart; !date.isAfter(weekStart.plusDays(6)); date = date.plusDays(1)) {
            ActivityLedger.DayStats day = ledger.peek(date);
            weekPlanned += day.planned;
            weekActual += day.actual;
            weekFocus += day.focus;
            weekCompleted += day.completed;
            weekMissed += day.missed;
        }
        // Planned minutes for the rest of the week (future sessions are outside the loaded range).
        weekPlanned += sessionRepository.findInRange(userId, TimeUtils.startOfDay(today.plusDays(1), zone),
                        TimeUtils.startOfDay(weekStart.plusDays(7), zone), SessionStatus.CANCELLED).stream()
                .mapToInt(StudySession::getPlannedMinutes).sum();

        List<DailyPoint> weekly = dailyPoints(ledger, today.minusDays(6), today);
        int activeRecent = ledger.activeDays(today.minusDays(CONSISTENCY_DAYS - 1L), today);

        List<DashboardOverviewResponse.UpcomingExam> exams = readinessService.overview(userId).stream()
                .limit(4)
                .map(item -> new DashboardOverviewResponse.UpcomingExam(item.examId(), item.examTitle(),
                        item.subjectName(), item.subjectColor(), item.examDate(), item.daysRemaining(),
                        item.readinessScore(), item.level(), item.dataSufficiency()))
                .toList();

        List<DashboardOverviewResponse.WeakTopic> weakTopics = masteryRepository.findAllWithTopic(userId).stream()
                .filter(m -> !m.getTopic().getSubject().isArchived())
                .filter(m -> m.getMasteryLevel() == MasteryLevel.WEAK
                        || m.getMasteryLevel() == MasteryLevel.NEEDS_IMPROVEMENT)
                .limit(5)
                .map(m -> new DashboardOverviewResponse.WeakTopic(m.getTopic().getId(), m.getTopic().getName(),
                        m.getTopic().getSubject().getName(), m.getTopic().getSubject().getColor(),
                        m.getMasteryScore().doubleValue(), m.getMasteryLevel().name()))
                .toList();

        RevisionDueResponse due = revisionService.due(userId);
        List<DashboardOverviewResponse.RevisionItem> revisionItems = new ArrayList<>();
        due.overdue().forEach(item -> revisionItems.add(new DashboardOverviewResponse.RevisionItem(item.topicId(),
                item.topicName(), item.subjectName(), item.nextRevisionDate(), item.daysOverdue())));
        due.dueToday().forEach(item -> revisionItems.add(new DashboardOverviewResponse.RevisionItem(item.topicId(),
                item.topicName(), item.subjectName(), item.nextRevisionDate(), item.daysOverdue())));

        return new DashboardOverviewResponse(today, zone.getId(), todaySummary,
                new DashboardOverviewResponse.WeekSummary(weekPlanned, weekActual, weekFocus, weekCompleted,
                        weekMissed),
                ledger.currentStreak(today, from),
                ScoreMath.round2(100.0 * activeRecent / CONSISTENCY_DAYS),
                exams, weakTopics,
                new DashboardOverviewResponse.RevisionSummary(due.dueCount(), due.overdueCount(),
                        revisionItems.stream().limit(5).toList()),
                weekly, recentActivity(userId, loaded.focus()), notificationService.unreadCount(userId));
    }

    @Transactional(readOnly = true)
    public WeeklyAnalyticsResponse weekly(Long userId, LocalDate anyDay) {
        User user = userService.require(userId);
        ZoneId zone = TimeUtils.zone(user.getTimeZone());
        LocalDate reference = anyDay != null ? anyDay : TimeUtils.today(clock, zone);
        LocalDate weekStart = TimetableGenerator.weekStart(reference);
        LocalDate weekEnd = weekStart.plusDays(6);
        ActivityLedger ledger = load(userId, zone, weekStart, weekEnd).ledger();
        List<DailyPoint> days = dailyPoints(ledger, weekStart, weekEnd);
        int planned = days.stream().mapToInt(DailyPoint::plannedMinutes).sum();
        int actual = days.stream().mapToInt(DailyPoint::actualMinutes).sum();
        int focus = days.stream().mapToInt(DailyPoint::focusMinutes).sum();
        int completed = days.stream().mapToInt(DailyPoint::completedSessions).sum();
        int missed = days.stream().mapToInt(DailyPoint::missedSessions).sum();
        int incomplete = 0;
        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            incomplete += ledger.peek(date).incomplete;
        }
        int finished = completed + incomplete + missed;
        double rate = finished == 0 ? 0.0 : ScoreMath.round2(100.0 * completed / finished);
        List<WeeklyAnalyticsResponse.SubjectMinutes> bySubject = ledger.subjects().values().stream()
                .sorted(Comparator.comparingInt((ActivityLedger.SubjectStats s) -> s.actual).reversed())
                .map(s -> new WeeklyAnalyticsResponse.SubjectMinutes(s.subjectId, s.name, s.color, s.planned,
                        s.actual))
                .toList();
        return new WeeklyAnalyticsResponse(weekStart, weekEnd, planned, actual, focus, completed, missed, rate, days,
                bySubject);
    }

    @Transactional(readOnly = true)
    public List<SubjectAnalyticsResponse> subjects(Long userId) {
        User user = userService.require(userId);
        ZoneId zone = TimeUtils.zone(user.getTimeZone());
        LocalDate today = TimeUtils.today(clock, zone);
        Map<Long, Object[]> totals = new HashMap<>();
        for (Object[] row : sessionRepository.studyTotalsBySubject(userId, SessionStatus.DONE)) {
            totals.put((Long) row[0], row);
        }
        ActivityLedger recent = load(userId, zone, today.minusDays(29), today).ledger();
        Map<Long, Integer> upcoming = new HashMap<>();
        for (StudySession session : sessionRepository.findInRange(userId, Instant.now(clock),
                TimeUtils.startOfDay(today.plusDays(8), zone), SessionStatus.CANCELLED)) {
            if (session.getStatus() == SessionStatus.PLANNED) {
                upcoming.merge(session.getSubject().getId(), session.getPlannedMinutes(), Integer::sum);
            }
        }
        Map<Long, Double> assessmentAverage = new HashMap<>();
        for (Object[] row : assessmentRepository.averageBySubject(userId)) {
            assessmentAverage.put((Long) row[0], ScoreMath.round2(((Number) row[1]).doubleValue()));
        }
        List<SubjectAnalyticsResponse> result = new ArrayList<>();
        for (SubjectResponse subject : subjectService.list(userId)) {
            if (subject.archived()) {
                continue;
            }
            Object[] total = totals.get(subject.id());
            int studied = total == null ? 0 : ((Number) total[1]).intValue();
            LocalDate lastStudied = total == null || total[2] == null
                    ? null
                    : TimeUtils.localDate((Instant) total[2], zone);
            ActivityLedger.SubjectStats recentStats = recent.subjects().get(subject.id());
            result.add(new SubjectAnalyticsResponse(subject.id(), subject.name(), subject.color(),
                    subject.topicCount(), subject.completedTopicCount(), subject.syllabusCompletion(),
                    subject.averageMastery(), subject.weakTopicCount(), studied,
                    recentStats == null ? 0 : recentStats.actual, upcoming.getOrDefault(subject.id(), 0),
                    assessmentAverage.get(subject.id()), lastStudied));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ProductivityResponse productivity(Long userId, int requestedDays) {
        int days = Math.max(7, Math.min(180, requestedDays));
        User user = userService.require(userId);
        ZoneId zone = TimeUtils.zone(user.getTimeZone());
        LocalDate today = TimeUtils.today(clock, zone);
        LocalDate rangeStart = today.minusDays(days - 1L);
        LocalDate monthStart = YearMonth.from(today).minusMonths(5).atDay(1);
        LocalDate loadFrom = monthStart.isBefore(rangeStart.minusDays(STREAK_LOOKBACK_DAYS))
                ? monthStart : rangeStart.minusDays(STREAK_LOOKBACK_DAYS);
        Loaded loaded = load(userId, zone, loadFrom, today);
        ActivityLedger ledger = loaded.ledger();

        List<DailyPoint> daily = dailyPoints(ledger, rangeStart, today);
        int planned = daily.stream().mapToInt(DailyPoint::plannedMinutes).sum();
        int actual = daily.stream().mapToInt(DailyPoint::actualMinutes).sum();
        int completed = 0;
        int incomplete = 0;
        int missed = 0;
        for (LocalDate date = rangeStart; !date.isAfter(today); date = date.plusDays(1)) {
            ActivityLedger.DayStats day = ledger.peek(date);
            completed += day.completed;
            incomplete += day.incomplete;
            missed += day.missed;
        }
        int finished = completed + incomplete + missed;
        int activeDays = ledger.activeDays(rangeStart, today);

        Map<YearMonth, int[]> monthly = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            monthly.put(YearMonth.from(today).minusMonths(i), new int[2]);
        }
        for (LocalDate date = monthStart; !date.isAfter(today); date = date.plusDays(1)) {
            int[] month = monthly.get(YearMonth.from(date));
            if (month != null) {
                ActivityLedger.DayStats day = ledger.peek(date);
                month[0] += day.actual;
                month[1] += day.focus;
            }
        }
        List<ProductivityResponse.MonthlyPoint> monthlyPoints = new ArrayList<>();
        monthly.forEach((month, values) -> monthlyPoints.add(new ProductivityResponse.MonthlyPoint(month.toString(),
                values[0], values[1])));

        List<RevisionLog> logs = revisionLogRepository.findBetween(userId, rangeStart, today);
        int onTime = (int) logs.stream().filter(RevisionLog::isOnTime).count();
        long overdueNow = topicRepository.countOverdueRevisions(userId, today);

        List<FocusSession> focusInRange = loaded.focus().stream()
                .filter(f -> f.getStatus() == FocusStatus.COMPLETED && f.getActualFocusSeconds() != null)
                .filter(f -> !TimeUtils.localDate(f.getStartedAt(), zone).isBefore(rangeStart))
                .toList();
        int focusMinutes = focusInRange.stream().mapToInt(f -> f.getActualFocusSeconds() / 60).sum();

        PerformanceSummaryResponse summary = performanceService.summary(userId);
        List<ProductivityResponse.WeakSubject> weakSubjects = summary.subjects().stream()
                .sorted(Comparator.comparing(PerformanceSummaryResponse.SubjectPerformance::averageMastery,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> new ProductivityResponse.WeakSubject(s.subjectId(), s.subjectName(), s.subjectColor(),
                        s.averageMastery(), s.weakTopics(), s.assessedTopics(), s.totalTopics()))
                .toList();

        Map<Long, ProductivityResponse.ReadinessTrend> trends = new LinkedHashMap<>();
        Map<Long, List<ProductivityResponse.TrendPoint>> points = new LinkedHashMap<>();
        for (ReadinessSnapshot snapshot : snapshotRepository.findSince(userId, rangeStart)) {
            Long examId = snapshot.getExam().getId();
            List<ProductivityResponse.TrendPoint> list = points.computeIfAbsent(examId, id -> new ArrayList<>());
            list.add(new ProductivityResponse.TrendPoint(snapshot.getSnapshotDate(),
                    ScoreMath.toDouble(snapshot.getReadinessScore())));
            trends.putIfAbsent(examId, new ProductivityResponse.ReadinessTrend(examId, snapshot.getExam().getTitle(),
                    snapshot.getExam().getSubject().getName(), snapshot.getExam().getSubject().getColor(),
                    snapshot.getExam().getExamDate(), list));
        }

        return new ProductivityResponse(days, planned, actual,
                planned == 0 ? 0.0 : ScoreMath.round2(100.0 * actual / planned),
                ScoreMath.round2(100.0 * activeDays / days), activeDays,
                ledger.currentStreak(today, loadFrom), ledger.longestStreak(loadFrom, today),
                new ProductivityResponse.SessionStats(completed, incomplete, missed,
                        finished == 0 ? 0.0 : ScoreMath.round2(100.0 * completed / finished)),
                new ProductivityResponse.RevisionStats(logs.size(), onTime,
                        logs.isEmpty() ? 0.0 : ScoreMath.round2(100.0 * onTime / logs.size()), overdueNow),
                new ProductivityResponse.FocusStats(focusInRange.size(), focusMinutes,
                        focusInRange.isEmpty() ? 0.0 : ScoreMath.round2((double) focusMinutes / focusInRange.size())),
                daily, monthlyPoints, weakSubjects, new ArrayList<>(trends.values()));
    }

    // ------------------------------------------------------------------ helpers

    private Loaded load(Long userId, ZoneId zone, LocalDate from, LocalDate to) {
        Instant start = TimeUtils.startOfDay(from, zone);
        Instant end = TimeUtils.startOfDay(to.plusDays(1), zone);
        ActivityLedger ledger = new ActivityLedger(zone);
        List<StudySession> sessions = sessionRepository.findInRange(userId, start, end, SessionStatus.CANCELLED);
        sessions.forEach(ledger::addSession);
        List<FocusSession> focus = focusRepository.findStartedSince(userId, start).stream()
                .filter(f -> f.getStartedAt().isBefore(end))
                .toList();
        focus.forEach(ledger::addFocus);
        revisionLogRepository.findBetween(userId, from, to).forEach(ledger::addRevision);
        return new Loaded(ledger, sessions, focus);
    }

    private static List<DailyPoint> dailyPoints(ActivityLedger ledger, LocalDate from, LocalDate to) {
        List<DailyPoint> points = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            ActivityLedger.DayStats day = ledger.peek(date);
            points.add(new DailyPoint(date, date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    day.planned, day.actual, day.focus, day.completed, day.missed));
        }
        return points;
    }

    private List<DashboardOverviewResponse.ActivityItem> recentActivity(Long userId, List<FocusSession> focus) {
        List<DashboardOverviewResponse.ActivityItem> items = new ArrayList<>();
        for (StudySession session : sessionRepository.findRecentlyFinished(userId, SessionStatus.DONE,
                PageRequest.of(0, 10))) {
            String title = session.getTopic() != null ? session.getTopic().getName() : session.getSubject().getName();
            items.add(new DashboardOverviewResponse.ActivityItem(
                    session.getStatus() == SessionStatus.COMPLETED ? "SESSION_COMPLETED" : "SESSION_INCOMPLETE",
                    title, "Studied %d min - %s".formatted(session.getActualMinutes() == null ? 0
                    : session.getActualMinutes(), session.getSubject().getName()), session.getCompletedAt()));
        }
        for (RevisionLog log : revisionLogRepository.findRecent(userId, PageRequest.of(0, 10))) {
            items.add(new DashboardOverviewResponse.ActivityItem("REVISION", log.getTopic().getName(),
                    "Revised (rated %s) - next on %s".formatted(log.getRating(), log.getNextRevisionDate()),
                    log.getRevisedAt()));
        }
        focus.stream()
                .filter(f -> f.getStatus() == FocusStatus.COMPLETED && f.getEndedAt() != null)
                .sorted(Comparator.comparing(FocusSession::getEndedAt).reversed())
                .limit(10)
                .forEach(f -> items.add(new DashboardOverviewResponse.ActivityItem("FOCUS",
                        f.getTopic() != null ? f.getTopic().getName()
                                : f.getSubject() != null ? f.getSubject().getName() : "Focus session",
                        "%d min of focused study".formatted(f.getActualFocusSeconds() == null ? 0
                                : f.getActualFocusSeconds() / 60),
                        f.getEndedAt())));
        for (Assessment assessment : assessmentRepository.findRecent(userId, PageRequest.of(0, 5))) {
            items.add(new DashboardOverviewResponse.ActivityItem("ASSESSMENT", assessment.getTitle(),
                    "Scored %.0f%% - %s".formatted(assessment.getScorePercent().doubleValue(),
                            assessment.getSubject().getName()), assessment.getCreatedAt()));
        }
        return items.stream()
                .filter(item -> item.occurredAt() != null)
                .sorted(Comparator.comparing(DashboardOverviewResponse.ActivityItem::occurredAt).reversed())
                .limit(10)
                .toList();
    }

    private record Loaded(ActivityLedger ledger, List<StudySession> sessions, List<FocusSession> focus) {
    }
}
