package com.smartstudy.notification;

import java.time.Instant;
import java.util.List;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        String link,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getLink(), notification.isRead(),
                notification.getCreatedAt());
    }

    /** GET /api/v1/notifications response. */
    public record Page(long unreadCount, List<NotificationResponse> items) {
    }
}
