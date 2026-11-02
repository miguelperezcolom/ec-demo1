package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.menu.MenuContext;
import io.mateu.ecdemo1.contenthub.application.menu.MenuLink;
import io.mateu.ecdemo1.contenthub.application.menu.ShownEntry;
import io.mateu.ecdemo1.contenthub.application.menu.SiteMenus;
import io.mateu.ecdemo1.contenthub.application.menu.SiteRoutes;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.data.FieldLink;
import io.mateu.uidl.data.Menu;
import io.mateu.uidl.data.RemoteMenu;
import io.mateu.uidl.data.RouteLink;
import io.mateu.uidl.interfaces.Actionable;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A site's menu as Mateu's entries, for one reader — what a {@code MenuSupplier} (IntranetMenu; a
 * newsletter's own) answers. Per request, so it follows the data and the reader's visibility; the same
 * entries in the Vaadin and the Redwood shells.
 *
 * <p>How each target becomes an entry, keeping every route as it was:
 * <ul>
 *   <li>a portal function → the {@link FieldLink} to its field (/intranet/noticias), the same entry the
 *       {@code @Menu} field made;</li>
 *   <li>a page, a section, a portal path → a {@link RouteLink} to the route the existing pages answer
 *       (/intranet/noticias/{id}, /intranet/secciones/{id});</li>
 *   <li>an external URL → a page of the portal that opens it in a new tab (Mateu's menu entries
 *       navigate inside the app);</li>
 *   <li>another Mateu UI → a {@link RemoteMenu}, federated as the shells federate the services; any other
 *       UI → a page of the portal that hosts it;</li>
 *   <li>a submenu → a {@link Menu} without a path of its own, so what it holds keeps its route.</li>
 * </ul>
 * Every field of the owner class the menu does not show at its top level is added hidden: routed, but
 * not drawn — so a function moved into a submenu, or left out, still answers its URL.
 */
@Component
@RequiredArgsConstructor
public class PortalMenu {

    static PortalMenu instance;

    final SiteMenus menus;
    final SiteRoutes routes;
    final Readers readers;

    @PostConstruct
    void register() {
        instance = this;
    }

    public static PortalMenu get() {
        return instance;
    }

    /**
     * @param siteId         whose menu
     * @param functionsOwner the class whose fields route the site's functions (IntranetMenu); a BUILTIN
     *                       entry is a link to one of its fields
     */
    public List<Actionable> actionables(String siteId, Class<?> functionsOwner, HttpRequest httpRequest) {
        return actionables(siteId, functionsOwner, menus.shown(context(siteId, httpRequest)));
    }

    public MenuContext context(String siteId, HttpRequest httpRequest) {
        return new MenuContext(siteId, readers.of(httpRequest), scopes(httpRequest));
    }

    static Set<String> scopes(HttpRequest httpRequest) {
        var header = httpRequest == null ? null : httpRequest.getHeaderValue(Readers.SCOPES_HEADER);
        return header == null || header.isBlank() ? Set.of() : Set.of(header.trim().split("\\s+"));
    }

    List<Actionable> actionables(String siteId, Class<?> functionsOwner, List<ShownEntry> shown) {
        var out = new ArrayList<Actionable>();
        var drawn = new HashSet<String>();
        for (var entry : shown) {
            out.add(actionable(siteId, functionsOwner, entry));
            if (entry.link() instanceof MenuLink.Function f) {
                drawn.add(f.key());
            }
        }
        for (var field : routedFields(functionsOwner)) {
            if (!drawn.contains(field)) {
                out.add(fieldLink(functionsOwner, field, field).withHidden(true));
            }
        }
        return out;
    }

    Actionable actionable(String siteId, Class<?> functionsOwner, ShownEntry entry) {
        if (entry.isSubmenu()) {
            // no path: its entries keep their own routes (/intranet/noticias, not /intranet/grupo/noticias)
            return new Menu(null, entry.label(), entry.children().stream()
                    .map(child -> actionable(siteId, functionsOwner, child)).toList());
        }
        return switch (entry.link()) {
            case MenuLink.Function f -> fieldLink(functionsOwner, f.key(), entry.label());
            case MenuLink.Route r -> routeLink(r.path(), entry.label());
            case MenuLink.External e -> routeLink(routes.externalLink(siteId, entry.id()), entry.label());
            case MenuLink.Remote r when r.source().kind() == io.mateu.ecdemo1.contenthub.domain.uisource.UiSourceKind.MATEU_REMOTE ->
                    new RemoteMenu(r.source().basePath(), r.route()).withLabel(entry.label())
                            .withPath("/" + r.source().id());
            case MenuLink.Remote r -> routeLink(routes.hostedUi(siteId, entry.id()), entry.label());
        };
    }

    static FieldLink fieldLink(Class<?> owner, String field, String label) {
        return FieldLink.builder().path("/" + field).label(label).serverSideType(owner.getName()).fieldName(field)
                .build();
    }

    static RouteLink routeLink(String route, String label) {
        return RouteLink.builder().path(route).route(route).label(label).build();
    }

    static List<String> routedFields(Class<?> owner) {
        return Arrays.stream(owner.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()))
                .filter(f -> !f.isSynthetic())
                .map(java.lang.reflect.Field::getName).toList();
    }
}
