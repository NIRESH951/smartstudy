package com.smartstudy.topic;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findByIdAndUserId(Long id, Long userId);

    @Query("select t from Topic t join fetch t.subject s where t.id = :id and t.userId = :userId")
    Optional<Topic> findOwnedWithSubject(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select t from Topic t join fetch t.subject s "
            + "where t.userId = :userId and s.id = :subjectId order by t.sortOrder asc, t.id asc")
    List<Topic> findBySubjectWithSubject(@Param("userId") Long userId, @Param("subjectId") Long subjectId);

    @Query("select t from Topic t join fetch t.subject s where t.userId = :userId "
            + "order by s.name asc, t.sortOrder asc, t.id asc")
    List<Topic> findAllWithSubject(@Param("userId") Long userId);

    /** Topics of subjects that are not archived - the planner's working set. */
    @Query("select t from Topic t join fetch t.subject s where t.userId = :userId and s.archived = false "
            + "order by s.name asc, t.sortOrder asc, t.id asc")
    List<Topic> findActiveWithSubject(@Param("userId") Long userId);

    @Query("select t from Topic t join fetch t.subject s where t.userId = :userId "
            + "and t.nextRevisionDate is not null and t.nextRevisionDate <= :until and s.archived = false "
            + "order by t.nextRevisionDate asc, t.id asc")
    List<Topic> findRevisionsDueBy(@Param("userId") Long userId, @Param("until") LocalDate until);

    @Query("select count(t) from Topic t where t.userId = :userId and t.subject.archived = false "
            + "and t.nextRevisionDate is not null and t.nextRevisionDate < :onDate")
    long countOverdueRevisions(@Param("userId") Long userId, @Param("onDate") LocalDate date);

    boolean existsBySubjectIdAndNameIgnoreCase(Long subjectId, String name);

    boolean existsBySubjectIdAndNameIgnoreCaseAndIdNot(Long subjectId, String name, Long id);

    @Query("select coalesce(max(t.sortOrder), 0) from Topic t where t.subject.id = :subjectId")
    int findMaxSortOrder(@Param("subjectId") Long subjectId);

    /** Rows of [subjectId, topicCount, completedCount]. */
    @Query("select t.subject.id, count(t), sum(case when t.status = :completed then 1 else 0 end) "
            + "from Topic t where t.userId = :userId group by t.subject.id")
    List<Object[]> countBySubject(@Param("userId") Long userId, @Param("completed") TopicStatus completed);
}
