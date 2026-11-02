package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.menu.MenuLink;
import io.mateu.ecdemo1.contenthub.application.menu.NewsletterMenus;
import io.mateu.ecdemo1.contenthub.application.menu.ShownEntry;
import io.mateu.ecdemo1.contenthub.application.menu.SiteMenus;
import io.mateu.ecdemo1.contenthub.application.menu.SiteRoutes;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * A newsletter site's menu as its site page draws it: the site's configurable menu (SiteMenus, owned by the
 * site's id — seeded with its home, sections, archive and pages; edited in Configuración → Menús), shown to
 * this reader by the same rules as the intranet's (active, audience, target resolved for them), and drawn as
 * the site's own bar inside its page — the shell's menu stays the intranet's. A submenu's entries are drawn
 * after its label.
 */
@Service
@RequiredArgsConstructor
public class NewsletterNavigation {

    final SiteMenus menus;
    final NewsletterMenus newsletterMenus;
    final SiteRoutes routes;

    /** An entry of the bar: its label, where it goes (its href also says which one is the current page). */
    public record Entry(String label, String href, int depth) {
    }

    public List<Entry> menu(HttpRequest httpRequest, Site site) {
        newsletterMenus.seedIfAbsent(site.id()); // a site created before its menu existed
        var out = new ArrayList<Entry>();
        flatten(menus.shown(PortalMenu.get().context(site.id(), httpRequest)), site.id(), 0, out);
        return out;
    }

    void flatten(List<ShownEntry> entries, String siteId, int depth, List<Entry> out) {
        for (var e : entries) {
            out.add(new Entry(e.label(), e.isSubmenu() ? null : href(siteId, e), depth));
            flatten(e.children(), siteId, depth + 1, out);
        }
    }

    String href(String siteId, ShownEntry e) {
        return switch (e.link()) {
            case MenuLink.Route r -> r.path();
            case MenuLink.Function f -> Ui.PORTAL + "/" + f.key();
            case MenuLink.External x -> x.url();
            case MenuLink.Remote r -> routes.hostedUi(siteId, e.id());
        };
    }

    public static String base(String siteId) {
        return Ui.PORTAL + "/n/" + siteId;
    }
}
