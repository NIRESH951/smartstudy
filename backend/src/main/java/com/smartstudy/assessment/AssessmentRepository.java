package com.smartstudy.assessment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    @Query("select a from Assessment a join fetch a.subject left join fetch a.exam "
            + "where a.id = :id and a.userId = :userId")
    Optional<Assessment> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select a from Assessment a join fetch a.subject left join fetch a.exam where a.userId = :userId "
            + "order by a.assessedOn desc, a.id desc")
    List<Assessment> findAllWithSubject(@Param("userId") Long userId);

    @Query("select a from Assessment a join fetch a.subject s left join fetch a.exam "
            + "where a.userId = :userId and s.id = :subjectId order by a.assessedOn desc, a.id desc")
    List<Assessment> findBySubjectWithSubject(@Param("userId") Long userId, @Param("subjectId") Long subjectId);

    @Query("select a from Assessment a where a.userId = :userId and a.subject.id = :subjectId "
            + "and a.assessmentType = :kind order by a.assessedOn desc, a.id desc")
    List<Assessment> findBySubjectAndType(@Param("userId") Long userId, @Param("subjectId") Long subjectId,
                                          @Param("kind") AssessmentType type);

    @Query("select a from Assessment a join fetch a.subject where a.userId = :userId order by a.createdAt desc")
    List<Assessment> findRecent(@Param("userId") Long userId, Pageable pageable);

    /** Rows of [subjectId, averagePercent, count]. */
    @Query("select a.subject.id, avg(a.scorePercent), count(a) from Assessment a where a.userId = :userId "
            + "group by a.subject.id")
    List<Object[]> averageBySubject(@Param("userId") Long userId);
}
