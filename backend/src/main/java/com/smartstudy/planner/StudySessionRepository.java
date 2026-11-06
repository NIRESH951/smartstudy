package com.smartstudy.planner;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    @Query("select s from StudySession s join fetch s.subject left join fetch s.topic "
            + "where s.id = :id and s.userId = :userId")
    Optional<StudySession> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    /** Sessions overlapping [from, to) except those with the given status (normally CANCELLED). */
    @Query("select s from StudySession s join fetch s.subject left join fetch s.topic "
            + "where s.userId = :userId and s.startAt < :rangeEnd and s.endAt > :rangeStart and s.status <> :excluded "
            + "order by s.startAt asc, s.id asc")
    List<StudySession> findInRange(@Param("userId") Long userId, @Param("rangeStart") Instant from,
                                   @Param("rangeEnd") Instant to, @Param("excluded") SessionStatus excluded);

    /** Conflict detection: sessions in the given statuses that overlap [start, end). */
    @Query("select s from StudySession s where s.userId = :userId and s.status in :statuses "
            + "and s.startAt < :windowEnd and s.endAt > :windowStart order by s.startAt asc")
    List<StudySession> findOverlapping(@Param("userId") Long userId, @Param("windowStart") Instant start,
                                       @Param("windowEnd") Instant end,
                                       @Param("statuses") Collection<SessionStatus> statuses);

    /** Planned sessions whose end lies before the cutoff - candidates for "missed". */
    @Query("select s from StudySession s join fetch s.subject left join fetch s.topic "
            + "where s.userId = :userId and s.status = :planned and s.endAt < :cutoff order by s.startAt asc")
    List<StudySession> findPlannedEndingBefore(@Param("userId") Long userId,
                                               @Param("planned") SessionStatus planned,
                                               @Param("cutoff") Instant cutoff);

    /** Removes the sessions the planner may freely move: planned, unlocked and in the future. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StudySession s where s.userId = :userId and s.status = :planned "
            + "and s.locked = false and s.startAt >= :rangeStart")
    int deleteReplaceable(@Param("userId") Long userId, @Param("planned") SessionStatus planned,
                          @Param("rangeStart") Instant from);

    /** Future sessions in the given statuses (e.g. locked planned sessions that will be kept). */
    @Query("select s from StudySession s join fetch s.subject left join fetch s.topic "
            + "where s.userId = :userId and s.status in :statuses and s.endAt > :rangeStart order by s.startAt asc")
    List<StudySession> findEndingAfter(@Param("userId") Long userId, @Param("rangeStart") Instant from,
                                       @Param("statuses") Collection<SessionStatus> statuses);

    /** Rows of [topicId, minutes] actually studied per topic for the given session type. */
    @Query("select s.topic.id, sum(coalesce(s.actualMinutes, 0)) from StudySession s "
            + "where s.userId = :userId and s.topic is not null and s.sessionType = :kind "
            + "and s.status in :statuses group by s.topic.id")
    List<Object[]> sumActualMinutesByTopic(@Param("userId") Long userId, @Param("kind") SessionType type,
                                           @Param("statuses") Collection<SessionStatus> statuses);

    /** Rows of [subjectId, minutes studied, latest session start] over all time. */
    @Query("select s.subject.id, sum(coalesce(s.actualMinutes, 0)), max(s.startAt) from StudySession s "
            + "where s.userId = :userId and s.status in :statuses group by s.subject.id")
    List<Object[]> studyTotalsBySubject(@Param("userId") Long userId,
                                        @Param("statuses") Collection<SessionStatus> statuses);

    @Query("select s from StudySession s join fetch s.subject left join fetch s.topic "
            + "where s.userId = :userId and s.topic.id = :topicId and s.sessionType = :kind "
            + "and s.status in :statuses and s.startAt >= :rangeStart and s.startAt < :rangeEnd order by s.startAt asc")
    List<StudySession> findTopicSessionsInRange(@Param("userId") Long userId, @Param("topicId") Long topicId,
                                                @Param("kind") SessionType type,
                                                @Param("statuses") Collection<SessionStatus> statuses,
                                                @Param("rangeStart") Instant from, @Param("rangeEnd") Instant to);

    @Query("select s from StudySession s join fetch s.subject left join fetch s.topic "
            + "where s.userId = :userId and s.status in :statuses and s.completedAt is not null "
            + "order by s.completedAt desc")
    List<StudySession> findRecentlyFinished(@Param("userId") Long userId,
                                            @Param("statuses") Collection<SessionStatus> statuses,
                                            Pageable pageable);
}
