package io.mateu.ecdemo1.contenthub.infra.config;

import io.mateu.ecdemo1.contenthub.application.menu.SiteRoutes;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

/**
 * The menus' seams and their defaults: where a site's routes hang (the intranet's, /intranet, for every
 * site until sites bring their own), and the registry of other UIs a menu may open (none until it is
 * plugged in: REMOTE entries are not shown). To replace one, declare a bean of the same type marked
 * {@code @Primary} — it wins over these.
 */
@Configuration
public class MenuConfig {

    @Bean
    SiteRoutes portalSiteRoutes() {
        return siteId -> Ui.PORTAL;
    }

    @Bean
    UiSources noUiSources() {
        return new UiSources() {
            @Override
            public Optional<UiSource> find(String sourceId) {
                return Optional.empty();
            }

            @Override
            public List<UiSource> all() {
                return List.of();
            }
        };
    }
}
