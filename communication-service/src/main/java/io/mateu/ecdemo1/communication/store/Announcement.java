package io.mateu.ecdemo1.communication.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One inbox item, handed to one channel: a chat space, or one browser. Retried with growing waits
 * until it goes or runs out of attempts; each channel on its own, so one that refuses does not hold
 * back the others.
 */
@Entity
@Table(name = "announcement", indexes = @Index(name = "announcement_due", columnList = "status, nextAttemptAt"))
public class Announcement {

    /** itemId|channel. */
    @Id
    @Column(length = 700)
    public String id;
    public String itemId;
    /** chat:1, chat:2 … or push:SUBSCRIPTION. */
    @Column(length = 100)
    public String channel;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    public AnnouncementStatus status;
    public int attempts;
    public Instant nextAttemptAt;
    public Instant sentAt;
    @Column(length = 1000)
    public String lastError;
}
