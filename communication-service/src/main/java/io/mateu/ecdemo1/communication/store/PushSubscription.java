package io.mateu.ecdemo1.communication.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A browser a person allowed to show notifications: where its push service takes them, and the keys
 * the payload is encrypted for. It receives what the recipients push to its person — by username, or
 * by the roles they had when they subscribed, refreshed every time the console loads — and the tasks
 * in their inbox.
 */
@Entity
@Table(name = "push_subscription")
public class PushSubscription {

    /** A hash of the endpoint: one row per browser. */
    @Id
    public String id;
    @Column(length = 2000)
    public String endpoint;
    @Column(length = 200)
    public String p256dh;
    @Column(length = 100)
    public String auth;
    public String username;
    @Column(length = 1000)
    public String roles;
    public Instant subscribedAt;
    public Instant lastSentAt;
}
