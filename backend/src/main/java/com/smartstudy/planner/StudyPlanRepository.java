package com.smartstudy.planner;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    Optional<StudyPlan> findFirstByUserIdAndStatusOrderByGeneratedAtDesc(Long userId, PlanStatus status);

    @Modifying(flushAutomatically = true)
    @Query("update StudyPlan p set p.status = :archived where p.userId = :userId and p.status = :active")
    int archive(@Param("userId") Long userId, @Param("active") PlanStatus active,
                @Param("archived") PlanStatus archived);
}
