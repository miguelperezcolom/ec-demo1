package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * THE SEAM for a newsletter site's menu. Today it is derived, in a fixed order: its home, its sections, the
 * archive, then its own published pages the reader sees (by title). When the configurable menus
 * (feat/configurable-menus: a Menu owned by {@code NEWSLETTER:<id>}) are merged, this one method switches
 * to reading that menu; nothing else draws a newsletter's navigation.
 */
@Service
@RequiredArgsConstructor
public class NewsletterNavigation {

    final NavigationRepository navigation;
    final Visibility visibility;

    /** An entry of the menu: its label, where it goes, and its key (what the current page highlights). */
    public record Entry(String key, String label, String href) {
    }

    public List<Entry> menu(Reader reader, Site site) {
        var base = base(site.id());
        var language = site.languageFor(reader.attributes().language());
        var out = new ArrayList<Entry>();
        out.add(new Entry("home", "Inicio", base));
        for (var s : navigation.get(site.id()).sections()) {
            if (s.parentId() == null) {
                out.add(new Entry("s:" + s.id(), s.name(language), base + "/secciones/" + s.id()));
            }
        }
        out.add(new Entry("archivo", "Números anteriores", base + "/archivo"));
        visibility.visibles(reader.attributes(), new ContentQuery(Set.of(ItemType.PAGE), null, null, null, 0, 50)
                        .in(site.id())).content().stream()
                .sorted(Comparator.comparing(i -> i.title() == null ? "" : i.title()))
                .forEach(p -> out.add(new Entry("p:" + p.itemId(), p.title(), base + "/p/" + p.itemId())));
        return out;
    }

    public static String base(String siteId) {
        return Ui.PORTAL + "/n/" + siteId;
    }
}
