package io.mateu.ecdemo1.users.infra.out.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A row in the identity outbox. One pending (or already delivered) instruction to make a user
 * exist in the identity provider.
 *
 * <p>Two of these columns are the whole reason the pattern works. {@code payload} is the provider
 * shape frozen as JSON at append time — so a change delivered an hour later carries the identity as
 * it was when saved, not as some later edit left it. {@code deliveredAt} is null until it lands and
 * a timestamp after; that nullability is the queue. Everything else is bookkeeping for retries.
 *
 * <p>The table indexes {@code deliveredAt} because the relay's query — undelivered, not abandoned,
 * due — filters on it every few seconds, and a sequential scan of a table that keeps its history
 * would get slower the longer the service runs. It is created by Hibernate, like the rest of this
 * service's schema; there is no migration to keep in step.
 */
@Entity
@Table(name = "identity_outbox", indexes = @Index(name = "idx_identity_outbox_pending",
        columnList = "deliveredAt, abandoned, nextAttemptAt"))
@Getter
@Setter
@NoArgsConstructor
public class OutboxEventEntity {

    @Id
    String id;

    /** The user this change is about — its {@code UserId}. Handy in a log; not otherwise read. */
    String aggregateId;

    /** {@code UserCreated} or {@code UserUpdated}. Kept for observability, not for routing. */
    String eventType;

    @Column(columnDefinition = "TEXT")
    String payload;

    Instant occurredAt;

    int attempts;

    Instant nextAttemptAt;

    /** Null while pending; the delivery time once it lands. This nullability is the queue. */
    Instant deliveredAt;

    /** Set when a change has failed too many times to keep retrying. Excluded from the queue. */
    boolean abandoned;

    @Column(columnDefinition = "TEXT")
    String lastError;
}
