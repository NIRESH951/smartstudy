package com.smartstudy.readiness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReadinessSnapshotRepository extends JpaRepository<ReadinessSnapshot, Long> {

    @Query("select r from ReadinessSnapshot r where r.exam.id = :examId and r.snapshotDate = :onDate")
    Optional<ReadinessSnapshot> findForExamOnDate(@Param("examId") Long examId, @Param("onDate") LocalDate date);

    @Query("select r from ReadinessSnapshot r where r.userId = :userId and r.exam.id = :examId "
            + "order by r.snapshotDate asc")
    List<ReadinessSnapshot> findHistory(@Param("userId") Long userId, @Param("examId") Long examId);

    @Query("select r from ReadinessSnapshot r join fetch r.exam e join fetch e.subject "
            + "where r.userId = :userId and r.snapshotDate >= :rangeStart order by r.snapshotDate asc")
    List<ReadinessSnapshot> findSince(@Param("userId") Long userId, @Param("rangeStart") LocalDate from);
}
