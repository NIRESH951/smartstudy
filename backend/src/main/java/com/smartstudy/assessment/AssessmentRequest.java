package com.smartstudy.assessment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Records subject-level marks, optionally broken down by topic.
 *
 * <pre>
 * POST /api/v1/assessments
 * { "subjectId": 3, "examId": null, "title": "Unit Test 2", "assessmentType": "QUIZ",
 *   "assessedOn": "2026-10-28", "maxMarks": 50, "obtainedMarks": 31, "notes": null,
 *   "topicScores": [
 *     { "topicId": 12, "maxMarks": 20, "obtainedMarks": 7 },
 *     { "topicId": 13, "maxMarks": 30, "obtainedMarks": 24 } ] }
 * </pre>
 */
public record AssessmentRequest(
        @NotNull Long subjectId,
        Long examId,
        @NotBlank @Size(max = 150) String title,
        @NotNull AssessmentType assessmentType,
        @NotNull LocalDate assessedOn,
        @NotNull @DecimalMin("0.01") @DecimalMax("10000") BigDecimal maxMarks,
        @NotNull @DecimalMin("0") @DecimalMax("10000") BigDecimal obtainedMarks,
        @Size(max = 1000) String notes,
        @Size(max = 50) List<@Valid TopicScoreRequest> topicScores) {

    public record TopicScoreRequest(
            @NotNull Long topicId,
            @NotNull @DecimalMin("0.01") @DecimalMax("10000") BigDecimal maxMarks,
            @NotNull @DecimalMin("0") @DecimalMax("10000") BigDecimal obtainedMarks) {
    }
}
