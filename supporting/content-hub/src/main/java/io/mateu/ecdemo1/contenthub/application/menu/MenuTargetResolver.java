package io.mateu.ecdemo1.contenthub.application.menu;

import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;

import java.util.List;
import java.util.Optional;

/**
 * One kind of menu target: what it is called in the configuration, what can be picked, and where it
 * takes a given reader — or nowhere, and the entry is not shown to them. The content-hub brings PAGE,
 * SECTION, BUILTIN, URL and REMOTE; a site type adds its own kinds (a newsletter's archive, an issue…)
 * by declaring one more bean of this type — {@link SiteMenus} picks every one up.
 */
public interface MenuTargetResolver {

    /** The {@link MenuTarget#kind()} it resolves (upper case). */
    String kind();

    /** Its name in the configuration's select ("Página", "Sección"…). */
    String label();

    /** Whether it applies to a site: a newsletter's archive means nothing on the intranet. */
    default boolean appliesTo(String siteId) {
        return true;
    }

    /**
     * Where the target takes this reader, or empty when they cannot go there — a page they do not see, a
     * function they cannot use, a UI source they may not open — and the entry is left out for them.
     */
    Optional<MenuLink> resolve(MenuContext context, MenuTarget target);

    /** What can be picked as the value, for the configuration's select; empty for free text (a URL). */
    List<Choice> choices(String siteId);

    /** The target in words, for the configuration's listing. */
    default String describe(String siteId, MenuTarget target) {
        return choices(siteId).stream().filter(c -> c.value().equals(target.value())).map(Choice::label)
                .findFirst().orElse(target.value());
    }

    record Choice(String value, String label) {
    }
}
