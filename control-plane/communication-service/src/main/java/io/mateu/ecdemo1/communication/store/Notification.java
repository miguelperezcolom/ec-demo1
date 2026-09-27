package io.mateu.ecdemo1.communication.store;

import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One notification the integration asked for, and what became of it. Its dedup key is unique: the
 * same key asked for again is the same notification, not another one.
 */
@Entity
@Table(name = "notification", indexes = @Index(columnList = "dedupKey", unique = true))
@NoArgsConstructor
@Getter
@Setter
public class Notification {

    @Id
    public String id;
    @Enumerated(EnumType.STRING)
    public NotificationType type;
    public String hotelCode;
    public String subject;
    @Column(length = 500)
    public String title;
    @Column(length = 4000)
    public String body;
    @Column(length = 1000)
    public String link;
    @Column(nullable = false)
    public String dedupKey;
    @Column(length = 2000)
    public String recipients;
    @Enumerated(EnumType.STRING)
    public DeliveryStatus status;
    public int attempts;
    @Column(length = 1000)
    public String lastError;
    public Instant requestedAt;
    public Instant sentAt;
    /** Urgent notifications also go to the chat space: SENT or FAILED, null if not posted there. */
    @Enumerated(EnumType.STRING)
    public DeliveryStatus chatStatus;
    /** With a default: added to a table that already has rows, a bare NOT NULL column is refused. */
    @Column(columnDefinition = "integer not null default 0")
    public int chatAttempts;
    @Column(length = 1000)
    public String chatError;
}
