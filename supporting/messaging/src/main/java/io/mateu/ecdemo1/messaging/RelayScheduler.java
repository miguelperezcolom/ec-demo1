package io.mateu.ecdemo1.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The relay's clock: a thread of its own, started once the application is ready (its bindings too),
 * that runs a pass every {@code messaging.outbox.interval} and the cleanup every
 * {@code messaging.outbox.cleanup-interval}. Its own thread rather than {@code @Scheduled}, so a service
 * need not enable scheduling for it and the passes make no {@code tasks.scheduled} observations — no
 * trace of their own in Tempo; each message sent is traced under the context it was written in.
 *
 * <p>Relays only where the service has a transport: without one (a laptop, a test with no broker) the
 * messages wait in the outbox.
 */
public class RelayScheduler implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    static final Logger log = LoggerFactory.getLogger(RelayScheduler.class);

    final Supplier<OutboxRelay> relay;
    final ObjectProvider<OutboxTransport> transport;
    final MessagingProperties.Outbox settings;
    ScheduledExecutorService executor;

    public RelayScheduler(Supplier<OutboxRelay> relay, ObjectProvider<OutboxTransport> transport,
                          MessagingProperties properties) {
        this.relay = relay;
        this.transport = transport;
        this.settings = properties.outbox();
    }

    @Override
    public synchronized void onApplicationEvent(ApplicationReadyEvent event) {
        if (executor != null) {
            return;
        }
        if (!settings.relay()) {
            log.info("Outbox {}: relay disabled (messaging.outbox.relay=false)", settings.table());
            return;
        }
        if (transport.getIfAvailable() == null) {
            log.info("Outbox {}: no transport, messages wait in the outbox", settings.table());
            return;
        }
        var relay = this.relay.get();
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "outbox-relay");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                relay.relayPending();
            } catch (RuntimeException e) {
                log.warn("Outbox relay pass failed: {}", e.toString());
            }
        }, settings.interval().toMillis(), settings.interval().toMillis(), TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(() -> {
            try {
                relay.cleanup();
            } catch (RuntimeException e) {
                log.warn("Outbox cleanup failed: {}", e.toString());
            }
        }, 60_000, settings.cleanupInterval().toMillis(), TimeUnit.MILLISECONDS);
        log.info("Outbox {}: relaying every {}", settings.table(), settings.interval());
    }

    @Override
    public synchronized void destroy() throws InterruptedException {
        if (executor != null) {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            executor = null;
        }
    }
}
