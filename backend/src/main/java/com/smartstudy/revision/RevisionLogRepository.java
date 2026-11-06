package com.smartstudy.revision;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RevisionLogRepository extends JpaRepository<RevisionLog, Long> {

    @Query("select l from RevisionLog l join fetch l.topic t join fetch t.subject "
            + "where l.userId = :userId order by l.revisedAt desc, l.id desc")
    List<RevisionLog> findRecent(@Param("userId") Long userId, Pageable pageable);

    @Query("select l from RevisionLog l join fetch l.topic t join fetch t.subject "
            + "where l.userId = :userId and t.id = :topicId order by l.revisedAt desc, l.id desc")
    List<RevisionLog> findRecentForTopic(@Param("userId") Long userId, @Param("topicId") Long topicId,
                                         Pageable pageable);

    @Query("select l from RevisionLog l where l.userId = :userId and l.revisedOn >= :rangeStart and l.revisedOn <= :rangeEnd")
    List<RevisionLog> findBetween(@Param("userId") Long userId, @Param("rangeStart") LocalDate from,
                                  @Param("rangeEnd") LocalDate to);

    @Query("select distinct l.topic.id from RevisionLog l where l.userId = :userId")
    List<Long> findRevisedTopicIds(@Param("userId") Long userId);
}
