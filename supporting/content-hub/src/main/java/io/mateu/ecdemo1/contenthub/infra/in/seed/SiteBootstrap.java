package io.mateu.ecdemo1.contenthub.infra.in.seed;

import io.mateu.ecdemo1.contenthub.application.usecases.Sites;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Before anything else at start: the intranet as a site. What existed before sites is already the
 * intranet's — the site columns default to it (schema.sql) — so this only stores the site itself; idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SiteBootstrap {

    final Sites sites;
    final io.mateu.ecdemo1.contenthub.application.menu.NewsletterMenus newsletterMenus;

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void start() {
        try {
            if (sites.ensureIntranet()) {
                log.info("The intranet stored as the default site");
            }
        } catch (RuntimeException e) {
            log.error("Could not store the intranet as a site", e);
        }
    }

    /** After the migration and the seed: every newsletter site gets its menu, if it has none (idempotent). */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void menus() {
        try {
            var n = newsletterMenus.seedAll();
            if (n > 0) {
                log.info("Menus seeded for {} newsletter site(s)", n);
            }
        } catch (RuntimeException e) {
            log.error("Could not seed the newsletters' menus", e);
        }
    }
}
