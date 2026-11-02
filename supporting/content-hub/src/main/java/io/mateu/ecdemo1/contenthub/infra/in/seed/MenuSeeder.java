package io.mateu.ecdemo1.contenthub.infra.in.seed;

import io.mateu.ecdemo1.contenthub.application.menu.DefaultMenus;
import io.mateu.ecdemo1.contenthub.application.menu.SiteMenus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The intranet's menu on first start: exactly the entries, order and labels the portal had before menus
 * were data, so nothing changes until an administrator changes it. Every boot, only if there is none (an
 * administrator's menu is left alone). Not demo content — the portal's own configuration — so it does not
 * depend on content-hub.seed.enabled; and the portal draws the same menu while nothing is stored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuSeeder {

    final SiteMenus menus;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            if (menus.seedIfAbsent(DefaultMenus.intranet())) {
                log.info("Intranet menu seeded");
            }
        } catch (RuntimeException e) {
            log.error("Could not seed the intranet menu (the portal draws the default one meanwhile)", e);
        }
    }
}
