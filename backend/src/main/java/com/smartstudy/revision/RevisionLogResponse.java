package com.smartstudy.revision;

import java.time.Instant;
import java.time.LocalDate;

public record RevisionLogResponse(
        Long id,
        Long topicId,
        String topicName,
        String subjectName,
        String subjectColor,
        RevisionRating rating,
        LocalDate revisedOn,
        LocalDate dueDate,
        boolean onTime,
        Integer durationMinutes,
        int previousIntervalDays,
        int newIntervalDays,
        double previousEaseFactor,
        double newEaseFactor,
        int repetitions,
        LocalDate nextRevisionDate,
        Instant revisedAt) {

    public static RevisionLogResponse from(RevisionLog log) {
        return new RevisionLogResponse(log.getId(), log.getTopic().getId(), log.getTopic().getName(),
                log.getTopic().getSubject().getName(), log.getTopic().getSubject().getColor(), log.getRating(),
                log.getRevisedOn(), log.getDueDate(), log.isOnTime(), log.getDurationMinutes(),
                log.getPreviousIntervalDays(), log.getNewIntervalDays(), log.getPreviousEaseFactor().doubleValue(),
                log.getNewEaseFactor().doubleValue(), log.getRepetitions(), log.getNextRevisionDate(),
                log.getRevisedAt());
    }
}
