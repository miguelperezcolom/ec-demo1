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
 * in their inbox. A browser at the front desk ({@link #FRONT_DESK}) receives only what the recipients
 * push to the desk (FRONT_DESK_PUSH): the desk has no inbox, and what the consoles are told is not
 * the desk's business.
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
    /** Where it was allowed: the consoles (null, what every browser was before) or {@link #FRONT_DESK}. */
    public static final String FRONT_DESK = "front-desk";

    public String username;
    @Column(length = 1000)
    public String roles;
    public Instant subscribedAt;
    public Instant lastSentAt;
    /** {@link #FRONT_DESK} for the front office's browsers; null for the consoles'. */
    public String app;

    public boolean atFrontDesk() {
        return FRONT_DESK.equals(app);
    }
}
