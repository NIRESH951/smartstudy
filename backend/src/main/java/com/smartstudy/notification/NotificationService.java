package com.smartstudy.notification;

import java.time.Clock;
import java.time.Instant;

import com.smartstudy.common.exception.ResourceNotFoundException;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and lists in-app notifications. */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /**
     * Creates a notification unless one with the same dedupe key already exists for the student.
     *
     * @return true when a new notification was created
     */
    @Transactional
    public boolean notify(Long userId, NotificationType type, String title, String message, String link,
                          String dedupeKey) {
        if (dedupeKey != null && notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return false;
        }
        notificationRepository.save(new Notification(userId, type, truncate(title, 150), truncate(message, 500),
                link, dedupeKey == null ? null : truncate(dedupeKey, 150)));
        return true;
    }

    @Transactional(readOnly = true)
    public NotificationResponse.Page list(Long userId, boolean unreadOnly, int limit) {
        int size = Math.max(1, Math.min(100, limit));
        var items = unreadOnly
                ? notificationRepository.findLatestUnread(userId, PageRequest.of(0, size))
                : notificationRepository.findLatest(userId, PageRequest.of(0, size));
        return new NotificationResponse.Page(notificationRepository.countUnread(userId),
                items.stream().map(NotificationResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countUnread(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.markRead(Instant.now(clock));
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, Instant.now(clock));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
