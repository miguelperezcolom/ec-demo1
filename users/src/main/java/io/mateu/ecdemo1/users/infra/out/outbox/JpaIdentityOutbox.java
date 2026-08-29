package io.mateu.ecdemo1.users.infra.out.outbox;

import io.mateu.core.infra.JsonSerializer;
import io.mateu.ecdemo1.users.application.out.identity.UserIdentity;
import io.mateu.ecdemo1.users.application.out.outbox.IdentityOutbox;
import io.mateu.ecdemo1.users.application.out.outbox.PendingIdentityChange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The identity outbox on Postgres, via the same JPA the rest of this service persists with.
 *
 * <p>Two things here are load-bearing. {@link #append} runs with no transaction of its own — the
 * default {@code REQUIRED} joins the caller's — which is what makes the row commit with the user
 * and not separately; annotating it with {@code REQUIRES_NEW} would quietly reopen the very window
 * the outbox exists to close. The retry bookkeeping ({@link #markDelivered}, {@link #reschedule})
 * does the opposite: each is its own short transaction, because the relay calls them one message at
 * a time and a failure on one must not roll back the outcome already recorded for another.
 *
 * <p>The stored payload is the {@link UserIdentity} as JSON. It is deserialized back on the way out
 * with the same {@link JsonSerializer} the repositories use, so a change carries the identity as it
 * was when appended — the point of freezing it rather than re-reading the user at delivery time.
 */
@Repository
@Slf4j
public class JpaIdentityOutbox implements IdentityOutbox {

    private final OutboxEventEntityRepository repository;
    private final int maxAttempts;
    private final long backoffBaseSeconds;
    private final long backoffCapSeconds;

    public JpaIdentityOutbox(OutboxEventEntityRepository repository,
                             @Value("${identity.outbox.max-attempts:10}") int maxAttempts,
                             @Value("${identity.outbox.backoff-seconds:10}") long backoffBaseSeconds,
                             @Value("${identity.outbox.backoff-cap-seconds:600}") long backoffCapSeconds) {
        this.repository = repository;
        this.maxAttempts = maxAttempts;
        this.backoffBaseSeconds = backoffBaseSeconds;
        this.backoffCapSeconds = backoffCapSeconds;
    }

    @Override
    @Transactional
    public void append(String aggregateId, String eventType, UserIdentity identity) {
        var now = Instant.now();
        var entity = new OutboxEventEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setAggregateId(aggregateId);
        entity.setEventType(eventType);
        entity.setPayload(JsonSerializer.toJson(identity));
        entity.setOccurredAt(now);
        entity.setAttempts(0);
        // Due immediately: the relay's next tick should pick it up, not wait out a backoff it has
        // not earned.
        entity.setNextAttemptAt(now);
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingIdentityChange> pending(int limit) {
        return repository
                .findByDeliveredAtIsNullAndAbandonedFalseAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc(
                        Instant.now(), PageRequest.of(0, limit))
                .stream()
                .map(e -> new PendingIdentityChange(e.getId(), e.getEventType(),
                        JsonSerializer.pojoFromJson(e.getPayload(), UserIdentity.class),
                        e.getAttempts()))
                .toList();
    }

    @Override
    @Transactional
    public void markDelivered(String id) {
        repository.findById(id).ifPresent(e -> {
            e.setDeliveredAt(Instant.now());
            e.setLastError(null);
            repository.save(e);
        });
    }

    @Override
    @Transactional
    public void reschedule(String id, String error) {
        repository.findById(id).ifPresent(e -> {
            e.setAttempts(e.getAttempts() + 1);
            e.setLastError(error);
            if (e.getAttempts() >= maxAttempts) {
                // Out of retries. Abandon it rather than let a permanently-failing change reappear
                // every tick forever — it stops delivering, but it also stops hiding: the row
                // stays, abandoned and with its last error, for someone to find and fix by hand.
                e.setAbandoned(true);
                log.error("Identity change {} ({} for user {}) abandoned after {} attempts. "
                                + "Last error: {}", e.getId(), e.getEventType(), e.getAggregateId(),
                        e.getAttempts(), error);
            } else {
                e.setNextAttemptAt(Instant.now().plus(backoff(e.getAttempts())));
            }
            repository.save(e);
        });
    }

    @Override
    @Transactional
    public int purgeDeliveredBefore(Duration retention) {
        var cutoff = Instant.now().minus(retention);
        return (int) repository.deleteByDeliveredAtIsNotNullAndDeliveredAtLessThan(cutoff);
    }

    /** Exponential, capped: base·2^(attempts-1), never more than the cap. */
    private Duration backoff(int attempts) {
        long seconds = backoffBaseSeconds;
        for (int i = 1; i < attempts && seconds < backoffCapSeconds; i++) {
            seconds = Math.min(backoffCapSeconds, seconds * 2);
        }
        return Duration.ofSeconds(Math.min(seconds, backoffCapSeconds));
    }
}
