package com.smartstudy.exam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * <pre>
 * { "id": 7, "subjectId": 3, "subjectName": "Data Structures", "subjectColor": "#3B82F6",
 *   "title": "End Semester Exam", "examType": "FINAL", "examDate": "2026-11-20", "startTime": "10:00:00",
 *   "durationMinutes": 180, "location": "Hall B", "targetScore": 85.0, "notes": null,
 *   "daysRemaining": 20, "upcoming": true, "createdAt": "2026-10-01T10:00:00Z" }
 * </pre>
 */
public record ExamResponse(
        Long id,
        Long subjectId,
        String subjectName,
        String subjectColor,
        String title,
        ExamType examType,
        LocalDate examDate,
        LocalTime startTime,
        Integer durationMinutes,
        String location,
        Double targetScore,
        String notes,
        int daysRemaining,
        boolean upcoming,
        Instant createdAt) {
}
