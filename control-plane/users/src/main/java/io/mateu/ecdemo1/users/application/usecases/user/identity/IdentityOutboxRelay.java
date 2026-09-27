package io.mateu.ecdemo1.users.application.usecases.user.identity;

import io.mateu.ecdemo1.users.application.out.identity.IdentityProviderPort;
import io.mateu.ecdemo1.users.application.out.outbox.IdentityOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Drains the identity outbox into the provider. The other half of the outbox pattern: {@code
 * append} put the intent in the database, this takes it out and delivers it, and the two are
 * deliberately not the same transaction — the write commits with the user, the send happens after,
 * on a timer, retried until it lands.
 *
 * <p>Each message is handled on its own. A delivery that throws is rescheduled and the loop moves
 * on rather than stopping, so one unreachable-at-the-moment change does not hold up the ones behind
 * it. A delivery that returns cleanly is marked done and never seen again. Because the provider's
 * upsert is idempotent, the failure mode this cannot avoid — delivered, then died before marking —
 * costs only a harmless second upsert on the next tick.
 *
 * <p>What it does not do is decide <em>when</em> to run or <em>how many</em> to take; a scheduler
 * in the infrastructure layer owns the clock and the batch size and calls {@link #runOnce}. Keeping
 * the trigger out here is what lets this be tested by calling a method rather than waiting for a
 * timer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityOutboxRelay {

    private final IdentityOutbox outbox;
    private final IdentityProviderPort identityProvider;

    /** Deliver up to {@code batchSize} pending changes. Safe to call on any thread, any schedule. */
    public void runOnce(int batchSize) {
        var batch = outbox.pending(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        int delivered = 0;
        for (var change : batch) {
            try {
                if (IdentityOutboxAppender.DELETED.equals(change.eventType())) {
                    identityProvider.deleteUser(change.identity());
                } else {
                    identityProvider.upsertUser(change.identity());
                }
                outbox.markDelivered(change.id());
                delivered++;
            } catch (Exception e) {
                log.warn("Identity change {} ({} for user {}) not delivered on attempt {}: {}",
                        change.id(), change.eventType(), change.identity().userId(),
                        change.attempts() + 1, e.toString());
                outbox.reschedule(change.id(), e.toString());
            }
        }
        if (delivered > 0) {
            log.info("Identity outbox: delivered {} of {} pending change(s)", delivered, batch.size());
        }
    }
}
