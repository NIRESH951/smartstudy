package com.smartstudy.planner;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.smartstudy.common.ScoreMath;
import com.smartstudy.common.TimeUtils;
import com.smartstudy.common.exception.BadRequestException;
import com.smartstudy.config.SmartStudyProperties;
import com.smartstudy.exam.Exam;
import com.smartstudy.exam.ExamRepository;
import com.smartstudy.notification.NotificationService;
import com.smartstudy.notification.NotificationType;
import com.smartstudy.performance.MasteryLevel;
import com.smartstudy.performance.TopicMastery;
import com.smartstudy.performance.TopicMasteryRepository;
import com.smartstudy.planner.engine.AvailabilityWindow;
import com.smartstudy.planner.engine.PlanningTask;
import com.smartstudy.planner.engine.PriorityBreakdown;
import com.smartstudy.planner.engine.PriorityCalculator;
import com.smartstudy.planner.engine.PriorityInput;
import com.smartstudy.planner.engine.TimeInterval;
import com.smartstudy.planner.engine.TimetableGenerator;
import com.smartstudy.planner.engine.TimetableRequest;
import com.smartstudy.planner.engine.TimetableResult;
import com.smartstudy.topic.Topic;
import com.smartstudy.topic.TopicRepository;
import com.smartstudy.topic.TopicStatus;
import com.smartstudy.user.AvailabilityService;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the Adaptive Study Planning Engine.
 *
 * <p><b>Generate / regenerate</b>:</p>
 * <ol>
 *   <li>Planned sessions whose end time has passed are marked MISSED.</li>
 *   <li>Every <em>replaceable</em> session (PLANNED, not locked, starting in the future) is deleted.
 *       Completed, incomplete, in-progress, missed and locked sessions are preserved.</li>
 *   <li>For every topic the remaining work is derived from real progress:
 *       {@code estimated minutes - minutes actually studied - minutes already reserved by locked
 *       sessions}. Missed or incomplete work therefore flows back into the plan automatically.</li>
 *   <li>Each task gets a weighted priority ({@link PriorityCalculator}) and the
 *       {@link TimetableGenerator} places the tasks into free, non-overlapping slots.</li>
 * </ol>
 * <p>Whenever a session is missed or left incomplete the active plan is regenerated from "now",
 * which is what makes the timetable adaptive.</p>
 */
@Service
public class PlannerService {

    private static final Set<SessionStatus> RESERVING = EnumSet.of(SessionStatus.PLANNED, SessionStatus.IN_PROGRESS);
    /** Remaining study below this many minutes is ignored (topic is effectively covered). */
    private static final int MIN_REMAINING_MINUTES = 10;
    private static final int MAX_CALENDAR_DAYS = 62;

    private final UserService userService;
    private final AvailabilityService availabilityService;
    private final TopicRepository topicRepository;
    private final TopicMasteryRepository masteryRepository;
    private final ExamRepository examRepository;
    private final StudySessionRepository sessionRepository;
    private final StudyPlanRepository planRepository;
    private final PriorityCalculator priorityCalculator;
    private final TimetableGenerator timetableGenerator;
    private final NotificationService notificationService;
    private final SmartStudyProperties properties;
    private final Clock clock;

    public PlannerService(UserService userService, AvailabilityService availabilityService,
                          TopicRepository topicRepository, TopicMasteryRepository masteryRepository,
                          ExamRepository examRepository, StudySessionRepository sessionRepository,
                          StudyPlanRepository planRepository, PriorityCalculator priorityCalculator,
                          TimetableGenerator timetableGenerator, NotificationService notificationService,
                          SmartStudyProperties properties, Clock clock) {
        this.userService = userService;
        this.availabilityService = availabilityService;
        this.topicRepository = topicRepository;
        this.masteryRepository = masteryRepository;
        this.examRepository = examRepository;
        this.sessionRepository = sessionRepository;
        this.planRepository = planRepository;
        this.priorityCalculator = priorityCalculator;
        this.timetableGenerator = timetableGenerator;
        this.notificationService = notificationService;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ generation

    @Transactional
    public PlanResponse generate(Long userId, GeneratePlanRequest request) {
        SmartStudyProperties.Planner config = properties.getPlanner();
        int days = request == null || request.days() == null ? config.getDefaultHorizonDays() : request.days();
        if (days < 1 || days > config.getMaxHorizonDays()) {
            throw new BadRequestException("days", "A plan can cover 1 to " + config.getMaxHorizonDays() + " days.");
        }
        User user = userService.require(userId);
        LocalDate today = TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
        LocalDate start = request == null || request.startDate() == null || request.startDate().isBefore(today)
                ? today
                : request.startDate();
        if (start.isAfter(today.plusDays(config.getMaxHorizonDays()))) {
            throw new BadRequestException("startDate", "The plan must start within the next "
                    + config.getMaxHorizonDays() + " days.");
        }
        return doGenerate(userId, start, start.plusDays(days - 1L), List.of()).response();
    }

    /** Rebuilds the active plan from today, keeping its end date (or the default horizon). */
    @Transactional
    public PlanResponse regenerate(Long userId) {
        User user = userService.require(userId);
        LocalDate today = TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
        return doGenerate(userId, today, regenerationEnd(userId, today), List.of()).response();
    }

    /**
     * Called after a session was missed or left incomplete. When an active plan exists it is
     * regenerated from now and every given session is linked to the first new session of the same
     * topic and type ({@code rescheduled_from_id}).
     *
     * @return map of original session id to its replacement (empty when nothing was rescheduled)
     */
    @Transactional
    public Map<Long, StudySession> rescheduleAfterChange(Long userId, List<SessionRef> changed) {
        User user = userService.require(userId);
        LocalDate today = TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
        Optional<StudyPlan> active = planRepository.findFirstByUserIdAndStatusOrderByGeneratedAtDesc(userId,
                PlanStatus.ACTIVE);
        if (active.isEmpty() || active.get().getEndDate().isBefore(today)) {
            return Map.of();
        }
        return doGenerate(userId, today, regenerationEnd(userId, today), changed).replacements();
    }

    /**
     * Marks overdue planned sessions as MISSED and, if any were found, reschedules their work and
     * notifies the student. Runs from the background job and before the planner views are shown.
     *
     * @return number of sessions newly marked as missed
     */
    @Transactional
    public int detectMissedAndReschedule(Long userId) {
        User user = userService.require(userId);
        ZoneId zone = TimeUtils.zone(user.getTimeZone());
        List<StudySession> missed = markOverdueAsMissed(userId, Instant.now(clock));
        if (missed.isEmpty()) {
            return 0;
        }
        List<SessionRef> refs = missed.stream().map(SessionRef::of).toList();
        String firstLabel = label(missed.getFirst(), zone);
        Long firstMissedId = missed.getFirst().getId();
        sessionRepository.flush();

        Map<Long, StudySession> replacements = rescheduleAfterChange(userId, refs);
        StringBuilder message = new StringBuilder();
        message.append(missed.size() == 1 ? "You missed " + firstLabel + "."
                : "You missed " + missed.size() + " study sessions, including " + firstLabel + ".");
        StudySession replacement = replacements.get(firstMissedId);
        if (replacement != null) {
            message.append(" It was rescheduled to ").append(label(replacement, zone)).append('.');
        } else if (!replacements.isEmpty()) {
            message.append(" Your timetable was updated to cover the remaining work.");
        } else {
            message.append(" Generate a new plan to reschedule the remaining work.");
        }
        notificationService.notify(userId,
                replacements.isEmpty() ? NotificationType.SESSION_MISSED : NotificationType.SESSION_RESCHEDULED,
                missed.size() == 1 ? "Missed study session" : "Missed study sessions",
                message.toString(), "/planner", "missed:" + firstMissedId);
        return missed.size();
    }

    List<StudySession> markOverdueAsMissed(Long userId, Instant now) {
        Instant cutoff = now.minusSeconds(properties.getPlanner().getMissedGraceMinutes() * 60L);
        List<StudySession> overdue = sessionRepository.findPlannedEndingBefore(userId, SessionStatus.PLANNED, cutoff);
        overdue.forEach(session -> session.setStatus(SessionStatus.MISSED));
        return overdue;
    }

    private LocalDate regenerationEnd(Long userId, LocalDate today) {
        SmartStudyProperties.Planner config = properties.getPlanner();
        LocalDate defaultEnd = today.plusDays(config.getDefaultHorizonDays() - 1L);
        LocalDate maxEnd = today.plusDays(config.getMaxHorizonDays() - 1L);
        return planRepository.findFirstByUserIdAndStatusOrderByGeneratedAtDesc(userId, PlanStatus.ACTIVE)
                .map(StudyPlan::getEndDate)
                .filter(end -> !end.isBefore(today))
                .map(end -> end.isAfter(maxEnd) ? maxEnd : end)
                .orElse(defaultEnd);
    }

    private GenerationOutcome doGenerate(Long userId, LocalDate start, LocalDate end, List<SessionRef> toLink) {
        Instant now = Instant.now(clock);

        // 1 + 2: settle the past, then remove everything the planner is allowed to move.
        List<SessionRef> links = new ArrayList<>(toLink);
        markOverdueAsMissed(userId, now).forEach(session -> links.add(SessionRef.of(session)));
        sessionRepository.deleteReplaceable(userId, SessionStatus.PLANNED, now);
        planRepository.archive(userId, PlanStatus.ACTIVE, PlanStatus.ARCHIVED);

        // 3: load a fresh planning context (the bulk delete cleared the persistence context).
        PlanningContext ctx = loadContext(userId);
        List<AvailabilityWindow> windows = availabilityService.windows(userId).stream()
                .map(w -> new AvailabilityWindow(DayOfWeek.of(w.getDayOfWeek()), w.getStartTime(), w.getEndTime()))
                .toList();

        LocalDate rangeFrom = TimetableGenerator.weekStart(start);
        LocalDate rangeTo = TimetableGenerator.weekStart(end).plusDays(7);
        List<StudySession> kept = sessionRepository.findInRange(userId,
                TimeUtils.startOfDay(rangeFrom, ctx.zone()), TimeUtils.startOfDay(rangeTo, ctx.zone()),
                SessionStatus.CANCELLED);
        List<TimeInterval> busy = new ArrayList<>();
        Map<LocalDate, Integer> usedByDay = new HashMap<>();
        Map<LocalDate, Integer> usedByWeek = new HashMap<>();
        for (StudySession session : kept) {
            if (!SessionStatus.BLOCKING.contains(session.getStatus())) {
                continue;
            }
            busy.add(new TimeInterval(session.getStartAt(), session.getEndAt()));
            int minutes = SessionStatus.DONE.contains(session.getStatus()) && session.getActualMinutes() != null
                    ? session.getActualMinutes()
                    : session.getPlannedMinutes();
            LocalDate date = TimeUtils.localDate(session.getStartAt(), ctx.zone());
            usedByDay.merge(date, minutes, Integer::sum);
            usedByWeek.merge(TimetableGenerator.weekStart(date), minutes, Integer::sum);
        }

        Map<Long, Integer> doneMinutes = studiedMinutesByTopic(userId);
        Map<Long, Integer> reservedStudy = new HashMap<>();
        Set<Long> reservedRevision = new HashSet<>();
        for (StudySession session : sessionRepository.findEndingAfter(userId, now, RESERVING)) {
            if (session.getTopic() == null) {
                continue;
            }
            if (session.getSessionType() == SessionType.STUDY) {
                reservedStudy.merge(session.getTopic().getId(), session.getPlannedMinutes(), Integer::sum);
            } else {
                reservedRevision.add(session.getTopic().getId());
            }
        }

        List<Candidate> candidates = buildCandidates(ctx, start, end, doneMinutes, reservedStudy, reservedRevision);
        List<PlanningTask> tasks = candidates.stream().map(Candidate::task).toList();
        Map<PlanningTask, Candidate> byTask = new HashMap<>();
        candidates.forEach(candidate -> byTask.put(candidate.task(), candidate));

        User user = ctx.user();
        TimetableResult result = timetableGenerator.generate(new TimetableRequest(start, end, ctx.zone(), now,
                windows, busy, usedByDay, usedByWeek, user.getDailyStudyMinutes(), user.getWeeklyStudyMinutes(),
                user.getPreferredSessionMinutes(), user.getBreakMinutes(), tasks));

        // 4: persist the plan and its sessions.
        StudyPlan plan = new StudyPlan(userId, start, end, now);
        plan.setTotalPlannedMinutes(result.totalMinutes());
        plan.setSessionCount(result.slots().size());
        plan.setStrategySummary(strategySummary(result, byTask));
        planRepository.save(plan);

        List<StudySession> created = new ArrayList<>();
        for (TimetableResult.PlannedSlot slot : result.slots()) {
            Candidate candidate = byTask.get(slot.task());
            Topic topic = candidate.topic();
            StudySession session = new StudySession(userId, topic.getSubject(), topic, slot.task().sessionType(),
                    SessionSource.GENERATED, slot.start(), slot.end());
            session.setPlan(plan);
            session.setPriorityScore(ScoreMath.toDecimal(slot.task().priority()));
            session.setReason(slot.task().reason());
            created.add(session);
        }
        sessionRepository.saveAll(created);

        // 5: link missed / incomplete sessions to the first new session covering the same work.
        Map<Long, StudySession> replacements = new LinkedHashMap<>();
        for (SessionRef ref : links) {
            if (ref.topicId() == null) {
                continue;
            }
            created.stream()
                    .filter(session -> session.getTopic().getId().equals(ref.topicId())
                            && session.getSessionType() == ref.sessionType()
                            && session.getRescheduledFrom() == null)
                    .min(Comparator.comparing(StudySession::getStartAt))
                    .ifPresent(session -> {
                        session.setRescheduledFrom(sessionRepository.getReferenceById(ref.sessionId()));
                        replacements.put(ref.sessionId(), session);
                    });
        }

        List<String> warnings = warnings(ctx, windows, user, candidates, result);
        List<StudySession> planSessions = sessionRepository.findInRange(userId,
                TimeUtils.startOfDay(start, ctx.zone()), TimeUtils.startOfDay(end.plusDays(1), ctx.zone()),
                SessionStatus.CANCELLED);
        List<PlanResponse.UnscheduledItem> unscheduled = result.unscheduled().stream()
                .map(work -> {
                    Topic topic = ctx.topicsById().get(work.topicId());
                    return new PlanResponse.UnscheduledItem(work.topicId(), topic.getName(),
                            topic.getSubject().getName(), work.sessionType(), work.remainingMinutes(), work.reason());
                })
                .toList();
        PlanResponse response = new PlanResponse(plan.getId(), start, end, now, plan.getTotalPlannedMinutes(),
                plan.getSessionCount(), plan.getStrategySummary(),
                planSessions.stream().map(session -> SessionResponse.from(session, ctx.zone())).toList(),
                unscheduled, warnings);
        return new GenerationOutcome(response, replacements);
    }

    // ------------------------------------------------------------------ priorities & recommendations

    /** Top topics to study right now, with the reasons behind their priority. */
    @Transactional(readOnly = true)
    public List<RecommendationResponse> recommendations(Long userId, int limit) {
        PlanningContext ctx = loadContext(userId);
        List<Candidate> candidates = buildCandidates(ctx, ctx.today(), ctx.today(), studiedMinutesByTopic(userId),
                Map.of(), Set.of());
        return candidates.stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.breakdown().priority()).reversed())
                .limit(Math.max(1, Math.min(50, limit)))
                .map(candidate -> toRecommendation(candidate, ctx))
                .toList();
    }

    /** Priority breakdown of every open task, for the transparency view of the planner page. */
    @Transactional(readOnly = true)
    public PrioritiesResponse priorities(Long userId) {
        SmartStudyProperties.Weights w = properties.getPlanner().getWeights();
        PlanningContext ctx = loadContext(userId);
        List<RecommendationResponse> items = buildCandidates(ctx, ctx.today(), ctx.today(),
                studiedMinutesByTopic(userId), Map.of(), Set.of()).stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.breakdown().priority()).reversed())
                .map(candidate -> toRecommendation(candidate, ctx))
                .toList();
        return new PrioritiesResponse(new PrioritiesResponse.Weights(w.getWeakness(), w.getExamUrgency(),
                w.getDifficulty(), w.getRevisionOverdue(), w.getSubjectImportance()), items);
    }

    private List<Candidate> buildCandidates(PlanningContext ctx, LocalDate start, LocalDate end,
                                            Map<Long, Integer> doneMinutes, Map<Long, Integer> reservedStudy,
                                            Set<Long> reservedRevision) {
        int revisionMinutes = properties.getPlanner().getRevisionSessionMinutes();
        List<Candidate> candidates = new ArrayList<>();
        for (Topic topic : ctx.topics()) {
            Long subjectId = topic.getSubject().getId();
            List<Exam> exams = ctx.upcomingExams().getOrDefault(subjectId, List.of());
            Exam nearest = exams.isEmpty() ? null : exams.getFirst();
            Exam latest = exams.isEmpty() ? null : exams.getLast();
            Integer daysUntilExam = nearest == null
                    ? null
                    : (int) ChronoUnit.DAYS.between(ctx.today(), nearest.getExamDate());
            Instant deadline = latest == null ? null : examStart(latest, ctx.zone());
            TopicMastery mastery = ctx.mastery().get(topic.getId());
            Double masteryScore = mastery == null ? null : mastery.getMasteryScore().doubleValue();
            LocalDate nextRevision = topic.getNextRevisionDate();
            Integer daysOverdue = nextRevision != null && !nextRevision.isAfter(ctx.today())
                    ? (int) ChronoUnit.DAYS.between(nextRevision, ctx.today())
                    : null;
            int importance = topic.getSubject().getImportance();

            if (topic.getStatus() != TopicStatus.COMPLETED) {
                int remaining = topic.getEstimatedMinutes() - doneMinutes.getOrDefault(topic.getId(), 0)
                        - reservedStudy.getOrDefault(topic.getId(), 0);
                if (remaining >= MIN_REMAINING_MINUTES) {
                    PriorityBreakdown breakdown = priorityCalculator.calculate(new PriorityInput(masteryScore,
                            daysUntilExam, topic.getDifficulty(), daysOverdue, importance));
                    candidates.add(new Candidate(topic, breakdown, daysUntilExam, masteryScore,
                            new PlanningTask(topic.getId(), subjectId, SessionType.STUDY, remaining, start, deadline,
                                    breakdown.priority(), breakdown.summary())));
                }
            }

            boolean revisionInRange = nextRevision != null && !nextRevision.isAfter(end);
            if (revisionInRange && !reservedRevision.contains(topic.getId())) {
                int overdueForTask = daysOverdue == null ? 0 : daysOverdue;
                PriorityBreakdown breakdown = priorityCalculator.calculate(new PriorityInput(masteryScore,
                        daysUntilExam, topic.getDifficulty(), overdueForTask, importance));
                LocalDate earliest = nextRevision.isBefore(start) ? start : nextRevision;
                String reason = revisionReason(nextRevision, ctx.today(), breakdown);
                candidates.add(new Candidate(topic, breakdown, daysUntilExam, masteryScore,
                        new PlanningTask(topic.getId(), subjectId, SessionType.REVISION, revisionMinutes, earliest,
                                null, breakdown.priority(), reason)));
            } else if (!revisionInRange && topic.getStatus() == TopicStatus.COMPLETED && masteryScore != null
                    && masteryScore < 40.0 && !reservedRevision.contains(topic.getId())) {
                PriorityBreakdown breakdown = priorityCalculator.calculate(new PriorityInput(masteryScore,
                        daysUntilExam, topic.getDifficulty(), daysOverdue, importance));
                String reason = "Extra practice: topic is completed but still weak (mastery %.0f%%)"
                        .formatted(masteryScore);
                candidates.add(new Candidate(topic, breakdown, daysUntilExam, masteryScore,
                        new PlanningTask(topic.getId(), subjectId, SessionType.PRACTICE, revisionMinutes, start,
                                deadline, breakdown.priority(), reason)));
            }
        }
        return candidates;
    }

    private static String revisionReason(LocalDate due, LocalDate today, PriorityBreakdown breakdown) {
        String head;
        if (due.isAfter(today)) {
            head = "Spaced-repetition review due on " + due;
        } else if (due.isEqual(today)) {
            head = "Spaced-repetition review due today";
        } else {
            long overdue = ChronoUnit.DAYS.between(due, today);
            head = "Spaced-repetition review overdue by " + overdue + " day" + (overdue == 1 ? "" : "s");
        }
        List<String> rest = breakdown.reasons().stream().filter(reason -> !reason.startsWith("Revision ")).toList();
        String text = rest.isEmpty() ? head : head + "; " + String.join("; ", rest);
        return text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }

    private RecommendationResponse toRecommendation(Candidate candidate, PlanningContext ctx) {
        Topic topic = candidate.topic();
        PriorityBreakdown b = candidate.breakdown();
        List<String> reasons = candidate.task().sessionType() == SessionType.STUDY
                ? b.reasons()
                : List.of(candidate.task().reason().split("; "));
        return new RecommendationResponse(topic.getId(), topic.getName(), topic.getSubject().getId(),
                topic.getSubject().getName(), topic.getSubject().getColor(), candidate.task().sessionType(),
                b.priority(),
                new RecommendationResponse.Factors(b.weakness(), b.examUrgency(), b.difficulty(),
                        b.revisionOverdue(), b.subjectImportance()),
                reasons, candidate.masteryScore(), MasteryLevel.of(candidate.masteryScore()).name(),
                candidate.daysUntilExam(), topic.getNextRevisionDate(), candidate.task().remainingMinutes());
    }

    // ------------------------------------------------------------------ views

    @Transactional
    public TodayResponse today(Long userId) {
        detectMissedAndReschedule(userId);
        User user = userService.require(userId);
        ZoneId zone = TimeUtils.zone(user.getTimeZone());
        LocalDate today = TimeUtils.today(clock, zone);
        DayPlanResponse day = buildCalendar(userId, zone, today, today).getFirst();
        int planned = 0;
        int completed = 0;
        int missed = 0;
        for (SessionResponse session : day.sessions()) {
            if (SessionStatus.DONE.contains(session.status())) {
                completed++;
            } else if (session.status() == SessionStatus.MISSED) {
                missed++;
            }
            planned++;
        }
        double percent = day.plannedMinutes() == 0 ? 0.0
                : ScoreMath.round2(Math.min(100.0, 100.0 * day.completedMinutes() / day.plannedMinutes()));
        PlanSummaryResponse activePlan = planRepository
                .findFirstByUserIdAndStatusOrderByGeneratedAtDesc(userId, PlanStatus.ACTIVE)
                .map(PlanSummaryResponse::from).orElse(null);
        return new TodayResponse(today, zone.getId(), day.plannedMinutes(), day.completedMinutes(), percent, planned,
                completed, missed, day.sessions(), recommendations(userId, 3), activePlan);
    }

    @Transactional
    public CalendarResponse calendar(Long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BadRequestException("to", "The end date must not be before the start date.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_CALENDAR_DAYS) {
            throw new BadRequestException("to", "The calendar range is limited to " + MAX_CALENDAR_DAYS + " days.");
        }
        detectMissedAndReschedule(userId);
        User user = userService.require(userId);
        ZoneId zone = TimeUtils.zone(user.getTimeZone());
        List<DayPlanResponse> days = buildCalendar(userId, zone, from, to);
        return new CalendarResponse(from, to, zone.getId(),
                days.stream().mapToInt(DayPlanResponse::plannedMinutes).sum(),
                days.stream().mapToInt(DayPlanResponse::completedMinutes).sum(), days);
    }

    /** Monday-Sunday week containing {@code anyDayOfWeek} (today when null). */
    @Transactional
    public CalendarResponse weekly(Long userId, LocalDate anyDayOfWeek) {
        User user = userService.require(userId);
        LocalDate reference = anyDayOfWeek != null
                ? anyDayOfWeek
                : TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
        LocalDate monday = TimetableGenerator.weekStart(reference);
        return calendar(userId, monday, monday.plusDays(6));
    }

    @Transactional(readOnly = true)
    public Optional<PlanSummaryResponse> activePlan(Long userId) {
        return planRepository.findFirstByUserIdAndStatusOrderByGeneratedAtDesc(userId, PlanStatus.ACTIVE)
                .map(PlanSummaryResponse::from);
    }

    private List<DayPlanResponse> buildCalendar(Long userId, ZoneId zone, LocalDate from, LocalDate to) {
        List<StudySession> sessions = sessionRepository.findInRange(userId, TimeUtils.startOfDay(from, zone),
                TimeUtils.startOfDay(to.plusDays(1), zone), SessionStatus.CANCELLED);
        Map<LocalDate, List<SessionResponse>> byDay = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            byDay.put(date, new ArrayList<>());
        }
        for (StudySession session : sessions) {
            LocalDate date = TimeUtils.localDate(session.getStartAt(), zone);
            List<SessionResponse> list = byDay.get(date);
            if (list != null) {
                list.add(SessionResponse.from(session, zone));
            }
        }
        List<DayPlanResponse> days = new ArrayList<>();
        byDay.forEach((date, list) -> {
            int planned = list.stream().mapToInt(SessionResponse::plannedMinutes).sum();
            int completed = list.stream()
                    .filter(s -> SessionStatus.DONE.contains(s.status()))
                    .mapToInt(s -> s.actualMinutes() == null ? 0 : s.actualMinutes())
                    .sum();
            days.add(new DayPlanResponse(date, planned, completed, list));
        });
        return days;
    }

    // ------------------------------------------------------------------ helpers

    private PlanningContext loadContext(Long userId) {
        User user = userService.require(userId);
        ZoneId zone = TimeUtils.zone(user.getTimeZone());
        LocalDate today = TimeUtils.today(clock, zone);
        List<Topic> topics = topicRepository.findActiveWithSubject(userId);
        Map<Long, Topic> topicsById = new HashMap<>();
        topics.forEach(topic -> topicsById.put(topic.getId(), topic));
        Map<Long, TopicMastery> mastery = new HashMap<>();
        for (TopicMastery item : masteryRepository.findAllForUser(userId)) {
            mastery.put(item.getTopic().getId(), item);
        }
        Map<Long, List<Exam>> upcoming = new HashMap<>();
        for (Exam exam : examRepository.findUpcoming(userId, today)) {
            upcoming.computeIfAbsent(exam.getSubject().getId(), id -> new ArrayList<>()).add(exam);
        }
        return new PlanningContext(user, zone, today, topics, topicsById, mastery, upcoming);
    }

    private Map<Long, Integer> studiedMinutesByTopic(Long userId) {
        Map<Long, Integer> done = new HashMap<>();
        for (Object[] row : sessionRepository.sumActualMinutesByTopic(userId, SessionType.STUDY, SessionStatus.DONE)) {
            done.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return done;
    }

    /** Preparation must be finished by the exam's start (or by the start of the exam day if no time is set). */
    private static Instant examStart(Exam exam, ZoneId zone) {
        return exam.getStartTime() != null
                ? TimeUtils.toInstant(exam.getExamDate(), exam.getStartTime(), zone)
                : TimeUtils.startOfDay(exam.getExamDate(), zone);
    }

    private static String strategySummary(TimetableResult result, Map<PlanningTask, Candidate> byTask) {
        if (result.slots().isEmpty()) {
            return "No sessions could be scheduled for this period.";
        }
        long topics = result.slots().stream().map(slot -> slot.task().topicId()).distinct().count();
        int minutes = result.totalMinutes();
        List<String> focus = result.slots().stream()
                .map(TimetableResult.PlannedSlot::task)
                .distinct()
                .sorted(Comparator.comparingDouble(PlanningTask::priority).reversed())
                .map(task -> byTask.get(task).topic().getName())
                .distinct()
                .limit(3)
                .toList();
        String summary = "%d sessions (%d h %d min) for %d topics. Focus: %s.".formatted(result.slots().size(),
                minutes / 60, minutes % 60, topics, String.join(", ", focus));
        return summary.length() <= 500 ? summary : summary.substring(0, 497) + "...";
    }

    private static List<String> warnings(PlanningContext ctx, List<AvailabilityWindow> windows, User user,
                                         List<Candidate> candidates, TimetableResult result) {
        List<String> warnings = new ArrayList<>();
        if (ctx.topics().isEmpty()) {
            warnings.add("Add subjects and topics before generating a study plan.");
        } else if (candidates.isEmpty()) {
            warnings.add("All topics are completed and no revisions are due in this period.");
        }
        if (windows.isEmpty()) {
            warnings.add("You have no weekly availability yet. Add study windows in Settings > Availability.");
        }
        if (user.getDailyStudyMinutes() == 0 || user.getWeeklyStudyMinutes() == 0) {
            warnings.add("Your daily or weekly study limit is 0 minutes, so nothing can be scheduled.");
        }
        if (!result.unscheduled().isEmpty()) {
            warnings.add(result.unscheduled().size() + " item(s) did not fit into your available time.");
        }
        return warnings;
    }

    private static String label(StudySession session, ZoneId zone) {
        String what = session.getTopic() != null ? session.getTopic().getName() : session.getSubject().getName();
        var start = session.getStartAt().atZone(zone);
        return "%s (%s %02d:%02d)".formatted(what,
                start.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                start.getHour(), start.getMinute());
    }

    private record PlanningContext(User user, ZoneId zone, LocalDate today, List<Topic> topics,
                                   Map<Long, Topic> topicsById, Map<Long, TopicMastery> mastery,
                                   Map<Long, List<Exam>> upcomingExams) {
    }

    private record Candidate(Topic topic, PriorityBreakdown breakdown, Integer daysUntilExam, Double masteryScore,
                             PlanningTask task) {
    }

    private record GenerationOutcome(PlanResponse response, Map<Long, StudySession> replacements) {
    }
}
