package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.RouteConstants;
import io.mateu.uidl.interfaces.CompiledRouteValue;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.RouteResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The portal's record pages as routes of their own — an item, a person, a section, a request — so
 * each is the page it builds (its page template: multimedia content, item overview…) and not a
 * listing's record view, which Mateu draws in its own page around the record's fields. The listings
 * reach them with {@code @RowRoute}; the links the portal draws (Ui.itemLink, People.link) are the
 * same URLs. Each page reads its id from the last path item.
 */
@Component
public class PortalRoutes implements RouteResolver {

    /**
     * The portal's root: the reader's home, as /intranet/inicio. The menu is a MenuSupplier group, and a
     * group with no page of its own was drawn by Mateu as an index of its entries.
     */
    static final String ROOT = "^/intranet/?$"; // anchored: some of Mateu's lookups match a prefix

    /** The UIs that mount the portal; /_content-hub has an "intranet" entry of its own (its configuration). */
    static final Set<String> PORTAL_UIS = Set.of("/_intranet", "/_portal");

    static final Map<String, Class<?>> PAGES = Map.ofEntries(
            Map.entry(ROOT, HomePage.class),
            Map.entry("/intranet/noticias/[^/]+", ItemPage.class),
            Map.entry("/intranet/personas/[^/]+", PersonPage.class),
            Map.entry("/intranet/secciones/[^/]+", SectionPage.class),
            Map.entry("/intranet/solicitudes/[^/]+", RequestPage.class),
            Map.entry("/intranet/encuestas/[^/]+", SurveyPage.class),
            // a reviewer's preview link: a draft, read-only, for anyone logged in who has the link
            Map.entry("/intranet/vista-previa/[^/]+", PreviewPage.class),
            // what a menu entry opens when the menu cannot: an external link, a hosted UI (MenuEntryPage)
            Map.entry("/intranet/enlace/[^/]+/[^/]+", MenuEntryPage.class),
            Map.entry("/intranet/ui/[^/]+/[^/]+", MenuEntryPage.class),
            // a newsletter's site: its home, archive, issues, sections and pages (NewsletterSitePage)
            Map.entry("/intranet/n/[^/]+(/.*)?", NewsletterSitePage.class));

    @Override
    public Class<?> resolveRoute(String route, String consumedRoute, HttpRequest httpRequest) {
        var path = route.contains("?") ? route.substring(0, route.indexOf('?')) : route;
        if (path.matches(ROOT) && (!portalUi(httpRequest) || !requestedRoot(httpRequest))) {
            // another UI's own "intranet" entry, or Mateu asking about the group on its way to one of
            // its entries (/intranet/favoritos under /_portal): not the root, left to the menu
            return null;
        }
        return PAGES.entrySet().stream().filter(e -> path.matches(e.getKey())).map(Map.Entry::getValue)
                .findFirst().orElse(Void.class);
    }

    /** Whether the request is to a UI that mounts the portal (Mateu's controller sets its baseUrl). */
    static boolean portalUi(HttpRequest httpRequest) {
        var baseUrl = httpRequest == null ? null : httpRequest.getAttribute("baseUrl");
        return baseUrl == null || PORTAL_UIS.contains(String.valueOf(baseUrl));
    }

    /** Whether the route the browser asked for is the root itself (unknown: assume it is). */
    static boolean requestedRoot(HttpRequest httpRequest) {
        var rq = httpRequest == null ? null : httpRequest.runActionRq();
        var asked = rq == null ? null : rq.route();
        if (asked == null) {
            return true;
        }
        var path = asked.contains("?") ? asked.substring(0, asked.indexOf('?')) : asked;
        return path.isEmpty() || path.matches(ROOT);
    }

    @Override
    public List<CompiledRouteValue> supportedRoutesPatterns() {
        // under whichever UI mounts the portal (/_intranet on the data plane, /_portal on its own host)
        return PAGES.keySet().stream().map(p -> new CompiledRouteValue(p, RouteConstants.NO_PARENT_ROUTE,
                Pattern.compile(p), Pattern.compile(".*"))).toList();
    }
}
