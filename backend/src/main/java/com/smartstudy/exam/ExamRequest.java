package com.smartstudy.exam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * <pre>
 * POST /api/v1/exams
 * { "subjectId": 3, "title": "End Semester Exam", "examType": "FINAL", "examDate": "2026-11-20",
 *   "startTime": "10:00", "durationMinutes": 180, "location": "Hall B", "targetScore": 85, "notes": null }
 * </pre>
 */
public record ExamRequest(
        @NotNull Long subjectId,
        @NotBlank @Size(max = 150) String title,
        @NotNull ExamType examType,
        @NotNull LocalDate examDate,
        LocalTime startTime,
        @Min(1) @Max(1440) Integer durationMinutes,
        @Size(max = 150) String location,
        @DecimalMin("0") @DecimalMax("100") BigDecimal targetScore,
        @Size(max = 1000) String notes) {
}
