package io.mateu.ecdemo1.contenthub.infra.config;

import io.mateu.ecdemo1.contenthub.application.menu.SiteRoutes;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The menus' seams and their defaults: where a site's routes hang (the intranet's, /intranet, for every
 * site until sites bring their own). To replace it, declare a bean of the same type marked
 * {@code @Primary}. The registry of other UIs a menu may open (UiSources) is the registry itself,
 * PgUiSourceRepository (Configuración → UIs externas): there is no empty default any more.
 */
@Configuration
public class MenuConfig {

    @Bean
    SiteRoutes portalSiteRoutes() {
        return siteId -> Ui.PORTAL;
    }
}
