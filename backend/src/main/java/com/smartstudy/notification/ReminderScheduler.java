package com.smartstudy.notification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import com.smartstudy.common.TimeUtils;
import com.smartstudy.exam.Exam;
import com.smartstudy.exam.ExamRepository;
import com.smartstudy.planner.PlannerService;
import com.smartstudy.readiness.ReadinessService;
import com.smartstudy.topic.Topic;
import com.smartstudy.topic.TopicRepository;
import com.smartstudy.user.User;
import com.smartstudy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background job (every 15 minutes by default) that keeps every student's data current:
 * <ol>
 *   <li>detects missed sessions and reschedules their work (with a notification),</li>
 *   <li>creates one "revisions due" reminder per day,</li>
 *   <li>creates exam reminders 7, 3 and 1 day(s) before and on the exam day,</li>
 *   <li>stores today's readiness snapshot of every upcoming exam (for the trend charts).</li>
 * </ol>
 * Dedupe keys make the job idempotent, so running it often never duplicates notifications.
 */
@Component
@ConditionalOnProperty(prefix = "smartstudy.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static final Set<Long> EXAM_REMINDER_DAYS = Set.of(7L, 3L, 1L, 0L);

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final ExamRepository examRepository;
    private final PlannerService plannerService;
    private final ReadinessService readinessService;
    private final NotificationService notificationService;
    private final Clock clock;

    public ReminderScheduler(UserRepository userRepository, TopicRepository topicRepository,
                             ExamRepository examRepository, PlannerService plannerService,
                             ReadinessService readinessService, NotificationService notificationService,
                             Clock clock) {
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.examRepository = examRepository;
        this.plannerService = plannerService;
        this.readinessService = readinessService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${smartstudy.scheduling.interval-ms:900000}",
            initialDelayString = "${smartstudy.scheduling.initial-delay-ms:60000}")
    public void run() {
        for (User user : userRepository.findByEnabledTrue()) {
            try {
                processUser(user);
            } catch (RuntimeException ex) {
                log.warn("Background processing failed for user {}: {}", user.getId(), ex.getMessage(), ex);
            }
        }
    }

    void processUser(User user) {
        Long userId = user.getId();
        LocalDate today = TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
        plannerService.detectMissedAndReschedule(userId);
        remindRevisions(userId, today);
        remindExams(userId, today);
        readinessService.snapshotUpcoming(userId);
    }

    private void remindRevisions(Long userId, LocalDate today) {
        List<Topic> due = topicRepository.findRevisionsDueBy(userId, today);
        if (due.isEmpty()) {
            return;
        }
        long overdue = due.stream().filter(topic -> topic.getNextRevisionDate().isBefore(today)).count();
        String names = String.join(", ", due.stream().limit(3).map(Topic::getName).toList())
                + (due.size() > 3 ? " and " + (due.size() - 3) + " more" : "");
        String message = "%d topic%s due for revision%s: %s.".formatted(due.size(), due.size() == 1 ? " is" : "s are",
                overdue > 0 ? " (" + overdue + " overdue)" : "", names);
        notificationService.notify(userId, NotificationType.REVISION_DUE, "Revision reminder", message, "/revision",
                "revision-due:" + today);
    }

    private void remindExams(Long userId, LocalDate today) {
        for (Exam exam : examRepository.findBetween(userId, today, today.plusDays(7))) {
            long daysLeft = ChronoUnit.DAYS.between(today, exam.getExamDate());
            if (!EXAM_REMINDER_DAYS.contains(daysLeft)) {
                continue;
            }
            String when = daysLeft == 0 ? "today" : daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
            notificationService.notify(userId, NotificationType.EXAM_UPCOMING,
                    "Exam " + when + ": " + exam.getTitle(),
                    "%s (%s) is on %s. Check your readiness and focus on weak topics.".formatted(exam.getTitle(),
                            exam.getSubject().getName(), exam.getExamDate()),
                    "/exams/readiness?examId=" + exam.getId(), "exam:" + exam.getId() + ":" + daysLeft);
        }
    }
}
