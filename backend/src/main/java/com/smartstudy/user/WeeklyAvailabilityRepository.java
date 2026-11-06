package com.smartstudy.user;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyAvailabilityRepository extends JpaRepository<WeeklyAvailability, Long> {

    List<WeeklyAvailability> findByUserIdOrderByDayOfWeekAscStartTimeAsc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WeeklyAvailability w where w.userId = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
