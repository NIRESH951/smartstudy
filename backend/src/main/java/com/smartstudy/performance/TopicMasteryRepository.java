package com.smartstudy.performance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicMasteryRepository extends JpaRepository<TopicMastery, Long> {

    @Query("select m from TopicMastery m where m.topic.id = :topicId")
    Optional<TopicMastery> findForTopic(@Param("topicId") Long topicId);

    @Query("select m from TopicMastery m where m.userId = :userId")
    List<TopicMastery> findAllForUser(@Param("userId") Long userId);

    @Query("select m from TopicMastery m join fetch m.topic t join fetch t.subject s "
            + "where m.userId = :userId order by m.masteryScore asc, t.id asc")
    List<TopicMastery> findAllWithTopic(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TopicMastery m where m.topic.id = :topicId")
    int deleteForTopic(@Param("topicId") Long topicId);
}
