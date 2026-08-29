package io.mateu.ecdemo1.users.infra.out.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OutboxEventEntityRepository extends JpaRepository<OutboxEventEntity, String> {

    /**
     * The due batch: not yet delivered, not abandoned, and past its next-attempt time. Ordered
     * oldest-first so the provider sees a user's changes in the order they happened, and paged so
     * one tick takes a bounded bite however far behind the outbox has fallen.
     */
    List<OutboxEventEntity> findByDeliveredAtIsNullAndAbandonedFalseAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc(
            Instant now, Pageable pageable);

    /** Delete delivered rows older than the cutoff. Returns how many went. */
    long deleteByDeliveredAtIsNotNullAndDeliveredAtLessThan(Instant cutoff);
}
