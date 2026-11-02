package io.mateu.ecdemo1.contenthub.application.menu;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MenuRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;

/**
 * A newsletter site's menu (SiteMenus, owned by the site's id): seeded the first time with what it used to
 * be derived from — its home, its top-level sections, its archive and its own pages — and from then on
 * configured like the intranet's (Configuración → Menús, content:admin). A page created from the newsletter
 * («Nueva página») is added at the end, so an editor does not need the configuration to put it there.
 */
@Service
@RequiredArgsConstructor
public class NewsletterMenus {

    final SiteMenus menus;
    final MenuRepository store;
    final SiteRepository sites;
    final NavigationRepository navigation;
    final ItemRepository items;

    /** The seed, if the site has no menu yet; idempotent. */
    public boolean seedIfAbsent(String siteId) {
        var site = sites.find(siteId).filter(s -> s.kind() == Site.Kind.NEWSLETTER).orElse(null);
        if (site == null || store.find(siteId).isPresent()) {
            return false;
        }
        return menus.seedIfAbsent(initial(site));
    }

    /** Seeds every newsletter site that has no menu (at start). */
    public int seedAll() {
        var n = 0;
        for (var s : sites.findAll()) {
            if (s.kind() == Site.Kind.NEWSLETTER && seedIfAbsent(s.id())) {
                n++;
            }
        }
        return n;
    }

    SiteMenu initial(Site site) {
        var language = site.definition().languages().isEmpty() ? SiteMenu.DEFAULT_LANGUAGE : site.definition().languages().getFirst();
        var menu = SiteMenu.empty(site.id(), language);
        menu.add("inicio", null, Map.of(language, "Inicio"), new MenuTarget(NewsletterTargets.HOME, site.id()), Audience.EVERYONE, true);
        for (var s : navigation.get(site.id()).sections()) {
            if (s.parentId() == null) {
                menu.add("s-" + s.id(), null, Map.of(language, s.name(language)), MenuTarget.section(s.id()), Audience.EVERYONE, true);
            }
        }
        menu.add("archivo", null, Map.of(language, "Números anteriores"), new MenuTarget(NewsletterTargets.ARCHIVE, site.id()),
                Audience.EVERYONE, true);
        items.findAll().stream().filter(i -> site.id().equals(i.siteId()) && i.type() == ItemType.PAGE)
                .sorted(Comparator.comparing(i -> i.draft().in(i.defaultLanguage(), i.defaultLanguage()).title()))
                .forEach(i -> menu.add("p-" + i.id(), null, Map.of(language, i.draft().in(i.defaultLanguage(),
                        i.defaultLanguage()).title()), MenuTarget.page(i.id()), Audience.EVERYONE, true));
        return menu;
    }

    /** A new page of the newsletter, at the end of its menu (shown once published and visible). */
    public void addPage(String siteId, String pageId, String title) {
        seedIfAbsent(siteId);
        var menu = menus.menu(siteId);
        var id = "p-" + pageId;
        if (menu.find(id).isPresent()) {
            return;
        }
        menu.add(id, null, Map.of(menu.defaultLanguage(), title == null || title.isBlank() ? pageId : title),
                MenuTarget.page(pageId), Audience.EVERYONE, true);
        store.save(menu);
    }
}
