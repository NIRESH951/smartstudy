package com.smartstudy.exam;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.smartstudy.common.ScoreMath;
import com.smartstudy.common.TimeUtils;
import com.smartstudy.common.exception.BadRequestException;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.subject.Subject;
import com.smartstudy.subject.SubjectService;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Examination management. */
@Service
public class ExamService {

    /** Exams may be recorded up to a year in the past (for history) and five years ahead. */
    private static final int MAX_PAST_DAYS = 366;
    private static final int MAX_FUTURE_DAYS = 365 * 5;

    private final ExamRepository examRepository;
    private final SubjectService subjectService;
    private final UserService userService;
    private final Clock clock;

    public ExamService(ExamRepository examRepository, SubjectService subjectService, UserService userService,
                       Clock clock) {
        this.examRepository = examRepository;
        this.subjectService = subjectService;
        this.userService = userService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Exam require(Long userId, Long examId) {
        return examRepository.findOwnedWithSubject(examId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam", examId));
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> list(Long userId, boolean upcomingOnly) {
        LocalDate today = today(userId);
        List<Exam> exams = upcomingOnly
                ? examRepository.findUpcoming(userId, today)
                : examRepository.findAllWithSubject(userId);
        return exams.stream().map(exam -> toResponse(exam, today)).toList();
    }

    @Transactional(readOnly = true)
    public ExamResponse get(Long userId, Long examId) {
        return toResponse(require(userId, examId), today(userId));
    }

    @Transactional
    public ExamResponse create(Long userId, ExamRequest request) {
        Subject subject = subjectService.require(userId, request.subjectId());
        LocalDate today = today(userId);
        validateDate(request.examDate(), today);
        Exam exam = new Exam(userId, subject, request.title().trim(), request.examDate());
        apply(exam, request);
        examRepository.save(exam);
        return toResponse(exam, today);
    }

    @Transactional
    public ExamResponse update(Long userId, Long examId, ExamRequest request) {
        Exam exam = require(userId, examId);
        LocalDate today = today(userId);
        validateDate(request.examDate(), today);
        if (!exam.getSubject().getId().equals(request.subjectId())) {
            exam.setSubject(subjectService.require(userId, request.subjectId()));
        }
        exam.setTitle(request.title().trim());
        exam.setExamDate(request.examDate());
        apply(exam, request);
        return toResponse(exam, today);
    }

    @Transactional
    public void delete(Long userId, Long examId) {
        Exam exam = require(userId, examId);
        examRepository.delete(exam);
        examRepository.flush();
    }

    private void apply(Exam exam, ExamRequest request) {
        exam.setExamType(request.examType());
        exam.setStartTime(request.startTime());
        exam.setDurationMinutes(request.durationMinutes());
        exam.setLocation(request.location() == null || request.location().isBlank() ? null : request.location().trim());
        exam.setTargetScore(request.targetScore());
        exam.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
    }

    private void validateDate(LocalDate examDate, LocalDate today) {
        if (examDate.isBefore(today.minusDays(MAX_PAST_DAYS)) || examDate.isAfter(today.plusDays(MAX_FUTURE_DAYS))) {
            throw new BadRequestException("examDate", "Exam date must be within one year in the past and five years "
                    + "in the future.");
        }
    }

    private LocalDate today(Long userId) {
        User user = userService.require(userId);
        return TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
    }

    public static ExamResponse toResponse(Exam exam, LocalDate today) {
        int days = (int) ChronoUnit.DAYS.between(today, exam.getExamDate());
        return new ExamResponse(exam.getId(), exam.getSubject().getId(), exam.getSubject().getName(),
                exam.getSubject().getColor(), exam.getTitle(), exam.getExamType(), exam.getExamDate(),
                exam.getStartTime(), exam.getDurationMinutes(), exam.getLocation(),
                ScoreMath.toDouble(exam.getTargetScore()), exam.getNotes(), days, days >= 0, exam.getCreatedAt());
    }
}
