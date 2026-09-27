package io.mateu.ecdemo1.crsintegration.inbox;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Where a consumer deduplicates what it receives. Delivery is at-least-once — every reconnection
 * repeats something — so a consumer records each event id in the same transaction as what it does
 * about it, and does nothing with one it has already recorded.
 */
@Component
@RequiredArgsConstructor
public class Inbox {

    final EntityManager entityManager;
    final Clock clock;

    /** @return true the first time this consumer sees the event, false on every repetition */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String consumer, String eventId) {
        return entityManager.createNativeQuery("""
                        insert into inbox_entry (consumer, event_id, received_at) values (?, ?, ?)
                        on conflict do nothing""")
                .setParameter(1, consumer)
                .setParameter(2, eventId)
                .setParameter(3, Instant.now(clock))
                .executeUpdate() == 1;
    }
}
