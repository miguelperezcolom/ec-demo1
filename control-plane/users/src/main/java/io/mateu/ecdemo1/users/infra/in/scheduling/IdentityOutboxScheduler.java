package io.mateu.ecdemo1.users.infra.in.scheduling;

import io.mateu.ecdemo1.users.application.out.outbox.IdentityOutbox;
import io.mateu.ecdemo1.users.application.usecases.user.identity.IdentityOutboxRelay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The clock behind the outbox relay. It owns the two things the relay deliberately does not — when
 * to run and how much to take — so the delivery logic stays a plain method that a test can call.
 *
 * <p>Two schedules, two jobs. The frequent one drains what is due; the daily one deletes what has
 * been delivered long enough to no longer be worth keeping. They are separate because they fail
 * differently: a delivery tick that throws should be tried again in seconds, while a purge that
 * throws can wait until tomorrow, and folding them together would tie the fast loop to the slow
 * one's fate.
 *
 * <p>This is an inbound adapter — a driver, like a controller or a message listener — which is why
 * it lives under {@code infra/in}. What drives the application from outside, a timer included,
 * belongs on the way in.
 */
@Component
@Slf4j
public class IdentityOutboxScheduler {

    private final IdentityOutboxRelay relay;
    private final IdentityOutbox outbox;
    private final int batchSize;
    private final Duration retention;

    public IdentityOutboxScheduler(IdentityOutboxRelay relay, IdentityOutbox outbox,
                                   @Value("${identity.outbox.batch-size:50}") int batchSize,
                                   @Value("${identity.outbox.retention-days:7}") long retentionDays) {
        this.relay = relay;
        this.outbox = outbox;
        this.batchSize = batchSize;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(fixedDelayString = "${identity.outbox.poll-interval-ms:5000}")
    public void deliverDue() {
        relay.runOnce(batchSize);
    }

    @Scheduled(cron = "${identity.outbox.purge-cron:0 0 3 * * *}")
    public void purgeOld() {
        int removed = outbox.purgeDeliveredBefore(retention);
        if (removed > 0) {
            log.info("Identity outbox: purged {} delivered change(s) older than {}", removed, retention);
        }
    }
}
