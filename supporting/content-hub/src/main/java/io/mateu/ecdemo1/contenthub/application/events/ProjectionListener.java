package io.mateu.ecdemo1.contenthub.application.events;

import io.mateu.ecdemo1.contenthub.application.visibility.Projection;
import io.mateu.ecdemo1.contenthub.application.visibility.ProjectionCache;
import io.mateu.ecdemo1.contenthub.domain.shared.ContentEvents;
import io.mateu.ecdemo1.contenthub.domain.shared.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Brings the projection and the caches in step with what happened, after the change committed. Off the
 * request's thread by default — publishing answers at once, and the backoffice shows when the change is
 * applied (eventual consistency, seconds) — and inline when {@code content-hub.projection.async} is false
 * (the tests).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectionListener {

    final Projection projection;
    final ProjectionCache cache;
    final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "projection");
        t.setDaemon(true);
        return t;
    });

    @Value("${content-hub.projection.async:true}")
    boolean async;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(DomainEvent event) {
        Runnable work = () -> {
            try {
                apply(event);
            } catch (RuntimeException e) {
                log.error("Could not apply {} to the projection", event, e);
            }
        };
        if (async) {
            executor.submit(work);
        } else {
            work.run();
        }
    }

    void apply(DomainEvent event) {
        switch (event) {
            case ContentEvents.ItemPublished e -> projection.projectItem(e.itemId());
            case ContentEvents.ItemRetired e -> projection.projectItem(e.itemId());
            case ContentEvents.AudienceRedefined e -> projection.rebuild();
            case ContentEvents.SlotVariantPublished e -> projection.projectSlot(e.slotKey());
            case ContentEvents.ItemBecameEffective e -> cache.invalidateAll();
            case ContentEvents.ItemExpired e -> cache.invalidateAll();
            case ContentEvents.NavigationChanged e -> cache.invalidateAll();
            case ContentEvents.EmployeeChanged e -> {
                if (e.oldHash() != null) {
                    cache.invalidateHash(e.oldHash());
                }
            }
        }
    }
}
