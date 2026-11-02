package io.mateu.ecdemo1.contenthub.infra.config;

import io.mateu.ecdemo1.contenthub.application.menu.SiteRoutes;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Each site's routes, as the portal serves them: the intranet's under /intranet (its pages at
 * /intranet/noticias/{id}), a newsletter's under /intranet/n/{site} (its pages at …/p/{id}, its sections at
 * …/secciones/{id} — NewsletterSitePage). The portal's functions, and the pages that open an external link
 * or host another UI, are the intranet's whatever the site.
 */
@Component
@Primary
public class SiteAwareRoutes implements SiteRoutes {

    @Override
    public String base(String siteId) {
        return intranet(siteId) ? Ui.PORTAL : Ui.PORTAL + "/n/" + siteId;
    }

    @Override
    public String page(String siteId, String pageId) {
        return intranet(siteId) ? Ui.PORTAL + "/noticias/" + pageId : base(siteId) + "/p/" + pageId;
    }

    @Override
    public String function(String siteId, String key) {
        return Ui.PORTAL + "/" + key;
    }

    @Override
    public String externalLink(String siteId, String entryId) {
        return Ui.PORTAL + "/enlace/" + siteId + "/" + entryId;
    }

    @Override
    public String hostedUi(String siteId, String entryId) {
        return Ui.PORTAL + "/ui/" + siteId + "/" + entryId;
    }

    static boolean intranet(String siteId) {
        return Site.INTRANET.equals(Site.of(siteId));
    }
}
