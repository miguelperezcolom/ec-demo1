package io.mateu.ecdemo1.contenthub.infra.in.jobs;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService;
import io.mateu.ecdemo1.contenthub.application.visibility.ContentQuestions;
import io.mateu.ecdemo1.contenthub.application.visibility.Projection;
import io.mateu.ecdemo1.contenthub.domain.shared.ContentEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * What runs by the clock. None of it decides visibility — that is read off the instant in every query —
 * these only tell caches and other systems that a date was crossed, send what is due, embed passages
 * for the chat, and forget profiles nobody has.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentJobs {

    final ItemRepository items;
    final DomainEvents events;
    final NewsletterService newsletter;
    final ContentQuestions questions;
    final Projection projection;
    final TransactionTemplate transactions;
    final Clock clock;

    Instant lastCrossing;

    @Scheduled(fixedDelayString = "${content-hub.jobs.crossings:PT30S}", initialDelayString = "PT20S")
    public void datesCrossed() {
        var now = clock.instant();
        var since = lastCrossing == null ? now.minusSeconds(60) : lastCrossing;
        lastCrossing = now;
        transactions.executeWithoutResult(s -> {
            for (var item : items.findAll()) {
                item.publishedVersion().ifPresent(v -> {
                    var c = v.content();
                    if (c.publishAt() != null && c.publishAt().isAfter(since) && !c.publishAt().isAfter(now)) {
                        events.publish(new ContentEvents.ItemBecameEffective(item.id()));
                    }
                    if (c.expireAt() != null && c.expireAt().isAfter(since) && !c.expireAt().isAfter(now)) {
                        events.publish(new ContentEvents.ItemExpired(item.id()));
                    }
                });
            }
        });
    }

    @Scheduled(fixedDelayString = "${content-hub.jobs.newsletter:PT30S}", initialDelayString = "PT30S")
    public void newsletters() {
        try {
            var sent = newsletter.sendDue();
            if (sent > 0) {
                log.info("Newsletter: {} emails sent", sent);
            }
        } catch (RuntimeException e) {
            log.warn("Newsletter pass failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${content-hub.jobs.embeddings:PT15S}", initialDelayString = "PT25S")
    public void embeddings() {
        try {
            int n;
            while ((n = questions.embedPending(32)) > 0) {
                log.info("Embedded {} passages", n);
            }
        } catch (RuntimeException e) {
            log.debug("Embeddings pass failed (will retry): {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${content-hub.jobs.orphans:PT1H}", initialDelayString = "PT5M")
    public void orphanProfiles() {
        projection.forgetOrphanProfiles();
    }
}
