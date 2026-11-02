package io.mateu.ecdemo1.contenthub.application.menu;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The target kinds the content-hub brings. Each asks the same questions the portal's pages ask — the
 * visibility function for a page or a section, the reader's scopes for another UI — so a menu never
 * shows the way to something its reader cannot open.
 */
public final class StandardTargets {

    private StandardTargets() {
    }

    /** A content-hub page: shown only to whoever can see it, and it goes to its route. */
    @Component
    @RequiredArgsConstructor
    public static class Pages implements MenuTargetResolver {

        final Visibility visibility;
        final ItemRepository items;
        final SiteRoutes routes;

        @Override
        public String kind() {
            return MenuTarget.PAGE;
        }

        @Override
        public String label() {
            return "Página";
        }

        @Override
        public Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
            if (!visibility.isVisible(context.reader().attributes(), target.value())) {
                return Optional.empty();
            }
            // under the site the page belongs to (a newsletter's own pages, under the newsletter)
            var site = items.find(target.value()).map(i -> i.siteId()).orElse(context.siteId());
            return Optional.of(new MenuLink.Route(routes.page(site, target.value())));
        }

        @Override
        public List<Choice> choices(String siteId) {
            return items.findAll().stream()
                    .filter(i -> i.type() == ItemType.PAGE && i.status() == ItemStatus.PUBLISHED)
                    .filter(i -> i.siteId().equals(io.mateu.ecdemo1.contenthub.domain.site.Site.of(siteId)))
                    .map(i -> new Choice(i.id(), i.draft().in(i.defaultLanguage(), i.defaultLanguage()).title()))
                    .toList();
        }
    }

    /** A section's listing: shown while something in it, or below it, is visible to the reader. */
    @Component
    @RequiredArgsConstructor
    public static class Sections implements MenuTargetResolver {

        final Visibility visibility;
        final NavigationRepository navigation;
        final SiteRoutes routes;

        @Override
        public String kind() {
            return MenuTarget.SECTION;
        }

        @Override
        public String label() {
            return "Sección";
        }

        @Override
        public Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
            // the site's own tree, and what of it the reader sees
            Set<String> shown = context.memo("sections.shown", () -> navigation.get(context.siteId())
                    .shown(visibility.sectionsWithVisibleContent(context.reader().attributes(), context.siteId())));
            return shown.contains(target.value())
                    ? Optional.of(new MenuLink.Route(routes.section(context.siteId(), target.value())))
                    : Optional.empty();
        }

        @Override
        public List<Choice> choices(String siteId) {
            var nav = navigation.get(siteId);
            return nav.sections().stream().map(s -> new Choice(s.id(),
                    "  ".repeat(nav.ancestors(s.id()).size()) + s.name("es"))).toList();
        }
    }

    /** One of the portal's own screens, for whoever can use it. */
    @Component
    public static class Builtins implements MenuTargetResolver {

        @Override
        public String kind() {
            return MenuTarget.BUILTIN;
        }

        @Override
        public String label() {
            return "Función del portal";
        }

        @Override
        public Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
            return PortalFunction.byKey(target.value()).filter(f -> f.usableBy(context.reader()))
                    .map(f -> new MenuLink.Function(f.key()));
        }

        @Override
        public List<Choice> choices(String siteId) {
            return Arrays.stream(PortalFunction.values()).map(f -> new Choice(f.key(), f.label())).toList();
        }
    }

    /** A path of the portal, or a URL outside it (a new tab). */
    @Component
    public static class Urls implements MenuTargetResolver {

        @Override
        public String kind() {
            return MenuTarget.URL;
        }

        @Override
        public String label() {
            return "Enlace (ruta o URL)";
        }

        @Override
        public Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
            return Optional.of(MenuTarget.isExternal(target.value()) ? new MenuLink.External(target.value())
                    : new MenuLink.Route(target.value()));
        }

        @Override
        public List<Choice> choices(String siteId) {
            return List.of();
        }
    }

    /**
     * Another UI, from the registry of UI sources: shown only to whoever its audience admits and who holds
     * the scopes it requires. With no registry plugged in, there is nothing to show.
     */
    @Component
    @RequiredArgsConstructor
    public static class Remotes implements MenuTargetResolver {

        final UiSources sources;
        final NamedAudiences namedAudiences;

        @Override
        public String kind() {
            return MenuTarget.REMOTE;
        }

        @Override
        public String label() {
            return "Otra interfaz";
        }

        @Override
        public Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
            VisibilityPolicy policy = context.memo("audiences.policy", namedAudiences::policy);
            return sources.find(target.remoteSource())
                    .filter(s -> policy.admits(s.audience(), context.reader().attributes()))
                    // active, and every scope it requires (RemoteUis.usable's rule)
                    .filter(s -> s.usableWith(context.scopes()))
                    .map(s -> new MenuLink.Remote(s, target.remoteRoute()));
        }

        @Override
        public List<Choice> choices(String siteId) {
            return sources.all().stream().map(s -> new Choice(s.id(), s.name() + " (" + s.basePath() + ")")).toList();
        }

        @Override
        public String describe(String siteId, MenuTarget target) {
            var source = sources.find(target.remoteSource()).map(io.mateu.ecdemo1.contenthub.domain.uisource.UiSource::name)
                    .orElse(target.remoteSource() + " (no registrada)");
            return source + target.remoteRoute();
        }
    }
}
