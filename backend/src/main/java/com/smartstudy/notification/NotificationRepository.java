package com.smartstudy.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Query("select n from Notification n where n.userId = :userId order by n.createdAt desc, n.id desc")
    List<Notification> findLatest(@Param("userId") Long userId, Pageable pageable);

    @Query("select n from Notification n where n.userId = :userId and n.read = false "
            + "order by n.createdAt desc, n.id desc")
    List<Notification> findLatestUnread(@Param("userId") Long userId, Pageable pageable);

    @Query("select count(n) from Notification n where n.userId = :userId and n.read = false")
    long countUnread(@Param("userId") Long userId);

    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.read = true, n.readAt = :readAt where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId, @Param("readAt") Instant now);
}
