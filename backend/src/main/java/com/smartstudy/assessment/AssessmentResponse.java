package com.smartstudy.assessment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AssessmentResponse(
        Long id,
        Long subjectId,
        String subjectName,
        String subjectColor,
        Long examId,
        String examTitle,
        String title,
        AssessmentType assessmentType,
        LocalDate assessedOn,
        double maxMarks,
        double obtainedMarks,
        double scorePercent,
        String notes,
        List<TopicScore> topicScores,
        Instant createdAt) {

    public record TopicScore(Long topicId, String topicName, double maxMarks, double obtainedMarks,
                             double scorePercent) {
    }

    public static AssessmentResponse from(Assessment assessment) {
        List<TopicScore> scores = assessment.getTopicScores().stream()
                .map(score -> new TopicScore(score.getTopic().getId(), score.getTopic().getName(),
                        score.getMaxMarks().doubleValue(), score.getObtainedMarks().doubleValue(),
                        score.getScorePercent().doubleValue()))
                .toList();
        return new AssessmentResponse(assessment.getId(), assessment.getSubject().getId(),
                assessment.getSubject().getName(), assessment.getSubject().getColor(),
                assessment.getExam() == null ? null : assessment.getExam().getId(),
                assessment.getExam() == null ? null : assessment.getExam().getTitle(),
                assessment.getTitle(), assessment.getAssessmentType(), assessment.getAssessedOn(),
                assessment.getMaxMarks().doubleValue(), assessment.getObtainedMarks().doubleValue(),
                assessment.getScorePercent().doubleValue(), assessment.getNotes(), scores,
                assessment.getCreatedAt());
    }
}
