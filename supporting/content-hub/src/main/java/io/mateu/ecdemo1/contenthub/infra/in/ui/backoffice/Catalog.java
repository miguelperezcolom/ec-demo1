package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.NamedAudience;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
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
        var nav = navigation.get();
        return nav.sections().stream().map(s -> new Option(s.id(),
                "  ".repeat(nav.ancestors(s.id()).size()) + s.name("es"))).toList();
    }

    public List<Option> publishedItems() {
        return items.findAll().stream().filter(i -> i.status() == ItemStatus.PUBLISHED)
                .map(i -> new Option(i.id(), i.draft().in(i.defaultLanguage(), i.defaultLanguage()).title()
                        + " (" + i.type().label() + ")")).toList();
    }

    public static List<Option> languages() {
        return List.of(new Option("es", "Español"), new Option("en", "English"), new Option("de", "Deutsch"));
    }
}
