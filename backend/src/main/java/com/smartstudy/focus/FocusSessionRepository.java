package com.smartstudy.focus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {

    @Query("select f from FocusSession f left join fetch f.subject left join fetch f.topic "
            + "where f.id = :id and f.userId = :userId")
    Optional<FocusSession> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select f from FocusSession f left join fetch f.subject left join fetch f.topic "
            + "where f.userId = :userId and f.status in :statuses order by f.startedAt desc")
    List<FocusSession> findByStatuses(@Param("userId") Long userId,
                                      @Param("statuses") Collection<FocusStatus> statuses);

    @Query("select f from FocusSession f left join fetch f.subject left join fetch f.topic "
            + "where f.userId = :userId and f.startedAt >= :rangeStart order by f.startedAt desc")
    List<FocusSession> findStartedSince(@Param("userId") Long userId, @Param("rangeStart") Instant from);

    @Query("select coalesce(sum(f.actualFocusSeconds), 0) from FocusSession f "
            + "where f.userId = :userId and f.studySession.id = :studySessionId and f.status = :status")
    long sumSecondsForStudySession(@Param("userId") Long userId, @Param("studySessionId") Long studySessionId,
                                   @Param("status") FocusStatus status);
}
