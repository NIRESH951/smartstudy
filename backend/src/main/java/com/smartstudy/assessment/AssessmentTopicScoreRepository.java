package com.smartstudy.assessment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentTopicScoreRepository extends JpaRepository<AssessmentTopicScore, Long> {

    /** All scores of a topic, oldest first (assessment date, then insertion order). */
    @Query("select s from AssessmentTopicScore s join fetch s.assessment a "
            + "where s.userId = :userId and s.topic.id = :topicId order by a.assessedOn asc, a.id asc")
    List<AssessmentTopicScore> findHistory(@Param("userId") Long userId, @Param("topicId") Long topicId);
}
