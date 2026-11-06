package com.smartstudy.assessment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.smartstudy.common.ScoreMath;
import com.smartstudy.common.TimeUtils;
import com.smartstudy.common.exception.BadRequestException;
import com.smartstudy.common.exception.ResourceNotFoundException;
import com.smartstudy.exam.Exam;
import com.smartstudy.exam.ExamService;
import com.smartstudy.performance.PerformanceService;
import com.smartstudy.subject.Subject;
import com.smartstudy.subject.SubjectService;
import com.smartstudy.topic.Topic;
import com.smartstudy.topic.TopicService;
import com.smartstudy.user.User;
import com.smartstudy.user.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records assessment marks and keeps topic mastery up to date. */
@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final SubjectService subjectService;
    private final TopicService topicService;
    private final ExamService examService;
    private final PerformanceService performanceService;
    private final UserService userService;
    private final Clock clock;

    public AssessmentService(AssessmentRepository assessmentRepository, SubjectService subjectService,
                             TopicService topicService, ExamService examService,
                             PerformanceService performanceService, UserService userService, Clock clock) {
        this.assessmentRepository = assessmentRepository;
        this.subjectService = subjectService;
        this.topicService = topicService;
        this.examService = examService;
        this.performanceService = performanceService;
        this.userService = userService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> list(Long userId, Long subjectId) {
        List<Assessment> assessments = subjectId == null
                ? assessmentRepository.findAllWithSubject(userId)
                : assessmentRepository.findBySubjectWithSubject(userId, subjectId);
        return assessments.stream().map(AssessmentResponse::from).toList();
    }

    @Transactional
    public AssessmentResponse create(Long userId, AssessmentRequest request) {
        User user = userService.require(userId);
        LocalDate today = TimeUtils.today(clock, TimeUtils.zone(user.getTimeZone()));
        if (request.assessedOn().isAfter(today)) {
            throw new BadRequestException("assessedOn", "The assessment date cannot be in the future.");
        }
        if (request.assessedOn().isBefore(today.minusYears(5))) {
            throw new BadRequestException("assessedOn", "The assessment date is too far in the past.");
        }
        requireMarks(request.obtainedMarks(), request.maxMarks(), "obtainedMarks");

        Subject subject = subjectService.require(userId, request.subjectId());
        Exam exam = null;
        if (request.examId() != null) {
            exam = examService.require(userId, request.examId());
            if (!exam.getSubject().getId().equals(subject.getId())) {
                throw new BadRequestException("examId", "The exam belongs to a different subject.");
            }
        }

        Assessment assessment = new Assessment(userId, subject, request.title().trim(), request.assessmentType(),
                request.assessedOn(), request.maxMarks(), request.obtainedMarks(),
                ScoreMath.percent(request.obtainedMarks(), request.maxMarks()));
        assessment.setExam(exam);
        assessment.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());

        Set<Long> seen = new HashSet<>();
        Set<Topic> affected = new LinkedHashSet<>();
        if (request.topicScores() != null) {
            for (AssessmentRequest.TopicScoreRequest score : request.topicScores()) {
                if (!seen.add(score.topicId())) {
                    throw new BadRequestException("topicScores", "Each topic can appear only once per assessment.");
                }
                requireMarks(score.obtainedMarks(), score.maxMarks(), "topicScores");
                Topic topic = topicService.require(userId, score.topicId());
                if (!topic.getSubject().getId().equals(subject.getId())) {
                    throw new BadRequestException("topicScores",
                            "Topic \"" + topic.getName() + "\" does not belong to " + subject.getName() + ".");
                }
                assessment.addTopicScore(new AssessmentTopicScore(userId, assessment, topic, score.maxMarks(),
                        score.obtainedMarks(), ScoreMath.percent(score.obtainedMarks(), score.maxMarks())));
                affected.add(topic);
            }
        }
        assessmentRepository.save(assessment);
        assessmentRepository.flush();
        affected.forEach(topic -> performanceService.recomputeTopic(userId, topic));
        return AssessmentResponse.from(assessment);
    }

    @Transactional
    public void delete(Long userId, Long assessmentId) {
        Assessment assessment = assessmentRepository.findOwned(assessmentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment", assessmentId));
        List<Topic> affected = assessment.getTopicScores().stream().map(AssessmentTopicScore::getTopic).toList();
        assessmentRepository.delete(assessment);
        assessmentRepository.flush();
        affected.forEach(topic -> performanceService.recomputeTopic(userId, topic));
    }

    private static void requireMarks(BigDecimal obtained, BigDecimal max, String field) {
        if (obtained.compareTo(max) > 0) {
            throw new BadRequestException(field, "Obtained marks cannot be greater than maximum marks.");
        }
    }
}
