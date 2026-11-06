package com.smartstudy.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.smartstudy.common.BaseEntity;

/**
 * In-app notification. {@code dedupeKey} (unique per user) prevents the background jobs from
 * creating the same reminder twice, e.g. {@code revision-due:2026-10-31}.
 */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "link", length = 255)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "dedupe_key", length = 150)
    private String dedupeKey;

    protected Notification() {
    }

    public Notification(Long userId, NotificationType type, String title, String message, String link,
                        String dedupeKey) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.link = link;
        this.dedupeKey = dedupeKey;
    }

    public void markRead(Instant when) {
        if (!read) {
            read = true;
            readAt = when;
        }
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }
}
