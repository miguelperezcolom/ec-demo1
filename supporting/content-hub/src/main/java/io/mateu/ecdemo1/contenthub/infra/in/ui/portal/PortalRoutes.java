package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.RouteConstants;
import io.mateu.uidl.interfaces.CompiledRouteValue;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.RouteResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
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

    static final Map<String, Class<?>> PAGES = Map.of(
            "/intranet/noticias/[^/]+", ItemPage.class,
            "/intranet/personas/[^/]+", PersonPage.class,
            "/intranet/secciones/[^/]+", SectionPage.class,
            "/intranet/solicitudes/[^/]+", RequestPage.class,
            "/intranet/encuestas/[^/]+", SurveyPage.class,
            // what a menu entry opens when the menu cannot: an external link, a hosted UI (MenuEntryPage)
            "/intranet/enlace/[^/]+/[^/]+", MenuEntryPage.class,
            "/intranet/ui/[^/]+/[^/]+", MenuEntryPage.class);

    @Override
    public Class<?> resolveRoute(String route, String consumedRoute, HttpRequest httpRequest) {
        var path = route.contains("?") ? route.substring(0, route.indexOf('?')) : route;
        return PAGES.entrySet().stream().filter(e -> path.matches(e.getKey())).map(Map.Entry::getValue)
                .findFirst().orElse(Void.class);
    }

    @Override
    public List<CompiledRouteValue> supportedRoutesPatterns() {
        // under whichever UI mounts the portal (/_intranet on the data plane, /_portal on its own host)
        return PAGES.keySet().stream().map(p -> new CompiledRouteValue(p, RouteConstants.NO_PARENT_ROUTE,
                Pattern.compile(p), Pattern.compile(".*"))).toList();
    }
}
