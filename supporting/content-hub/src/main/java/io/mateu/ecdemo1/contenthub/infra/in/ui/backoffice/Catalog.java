package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.NamedAudience;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.uidl.data.Option;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The options the backoffice's selects offer: the real values of each attribute (those the employee
 * directory holds — the users service's catalogue codes), the named audiences, the sections, the
 * published items. Reachable statically because the forms' nested rows (blocks, variants) are not beans.
 */
@Component
@RequiredArgsConstructor
public class Catalog {

    static Catalog instance;

    final JdbcTemplate jdbc;
    final NamedAudienceRepository audiences;
    final NavigationRepository navigation;
    final ItemRepository items;
    final io.mateu.ecdemo1.contenthub.application.out.MediaRepository media;
    final io.mateu.ecdemo1.contenthub.application.out.UiSources uiSources;

    @PostConstruct
    void register() {
        instance = this;
    }

    public static Catalog get() {
        return instance;
    }

    public List<Option> values(Attribute attribute) {
        return jdbc.queryForList("select distinct attributes ->> ? as v from ch_employee where attributes ->> ? is not null "
                + "order by v", String.class, attribute.name(), attribute.name()).stream()
                .map(v -> new Option(v, v)).toList();
    }

    public List<Option> namedAudiences() {
        return audiences.findAll().stream().map(a -> new Option(a.id(), a.name())).toList();
    }

    public String namedAudienceLabel(String id) {
        return audiences.find(id).map(NamedAudience::name).orElse(id);
    }

    public List<Option> sections() {
        return sections(io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET);
    }

    /** A site's sections. */
    public List<Option> sections(String siteId) {
        var nav = navigation.get(siteId);
        return nav.sections().stream().map(s -> new Option(s.id(),
                "  ".repeat(nav.ancestors(s.id()).size()) + s.name("es"))).toList();
    }

    public List<Option> publishedItems() {
        return items.findAll().stream().filter(i -> i.status() == ItemStatus.PUBLISHED)
                .map(i -> new Option(i.id(), i.draft().in(i.defaultLanguage(), i.defaultLanguage()).title()
                        + " (" + i.type().label() + ")")).toList();
    }

    /** The published pages: what can be a home. */
    public List<Option> publishedPages() {
        return items.findAll().stream().filter(i -> i.status() == ItemStatus.PUBLISHED && i.type() == ItemType.PAGE)
                .map(i -> new Option(i.id(), i.draft().in(i.defaultLanguage(), i.defaultLanguage()).title())).toList();
    }

    /** The media library, newest first: what a MEDIA block shows, and what can be embedded with [[media:id]]. */
    public List<Option> media() {
        return media.search("", 0, 200).stream()
                .map(m -> new Option(m.id(), m.title() + " (" + m.kind().name().toLowerCase() + ")")).toList();
    }

    /** The registry of external UIs: what a remote page or a «Componente remoto» block may show. */
    public List<Option> uiSources() {
        return uiSources.all().stream().map(s -> new Option(s.id(), s.name() + " (" + s.kind().label()
                + (s.active() ? "" : ", inactiva") + ")")).toList();
    }

    public static List<Option> languages() {
        return List.of(new Option("es", "Español"), new Option("en", "English"), new Option("de", "Deutsch"));
    }
}
