package io.mateu.ecdemo1.contenthub.application.menu;

import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;

/**
 * Where a shown entry takes the reader, once its target is resolved for them — what a renderer needs,
 * whatever kind of target it came from.
 */
public sealed interface MenuLink permits MenuLink.Function, MenuLink.Route, MenuLink.External, MenuLink.Remote {

    /** One of the portal's own screens, by its key (the {@code @Menu}-less field that routes it). */
    record Function(String key) implements MenuLink {
    }

    /** A route of the site (/intranet/noticias/{id}, /intranet/secciones/{id}…), resolved as today. */
    record Route(String path) implements MenuLink {
    }

    /** Outside the portal: opened in a new tab. */
    record External(String url) implements MenuLink {
    }

    /** Another UI, from the registry of UI sources, and a route inside it ("" for its home). */
    record Remote(UiSource source, String route) implements MenuLink {
    }
}
