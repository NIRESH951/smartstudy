package com.smartstudy.revision;

import java.time.LocalDate;

/**
 * <pre>
 * { "topicId": 12, "topicName": "AVL Trees", "rating": "GOOD", "revisedOn": "2026-10-31",
 *   "previousIntervalDays": 6, "newIntervalDays": 15, "previousEaseFactor": 2.5, "newEaseFactor": 2.5,
 *   "repetitions": 3, "nextRevisionDate": "2026-11-15",
 *   "explanation": "Rated GOOD: interval 6 -> 15 days, ease factor 2.50 -> 2.50. Next revision on 2026-11-15.",
 *   "completedSessionId": 44 }
 * </pre>
 */
public record RevisionResultResponse(
        Long topicId,
        String topicName,
        RevisionRating rating,
        LocalDate revisedOn,
        int previousIntervalDays,
        int newIntervalDays,
        double previousEaseFactor,
        double newEaseFactor,
        int repetitions,
        LocalDate nextRevisionDate,
        String explanation,
        Long completedSessionId) {
}
