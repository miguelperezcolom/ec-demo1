package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.core.infra.HeadlessHttpRequest;
import io.mateu.dtos.RunActionRqDto;
import io.mateu.uidl.RouteConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The portal's own routes. The root (/intranet) is the reader's home, as it was while the menu was
 * annotated fields — now the menu is a MenuSupplier group, which Mateu would draw as an index of entries.
 */
class PortalRoutesTest {

    final PortalRoutes routes = new PortalRoutes();

    static HeadlessHttpRequest request(String baseUrl, String askedRoute) {
        return new HeadlessHttpRequest(RunActionRqDto.builder().route(askedRoute).build())
                .withAttribute("baseUrl", baseUrl);
    }

    @Test
    void the_root_opens_the_home_in_the_uis_that_mount_the_portal() {
        for (var ui : new String[]{"/_intranet", "/_portal"}) {
            for (var root : new String[]{"/intranet", "/intranet/", "/intranet?x=1"}) {
                assertThat(routes.supportsRoute(root, RouteConstants.NO_PARENT_ROUTE)).isTrue();
                assertThat(routes.resolveRoute(root, ui, request(ui, root))).as(ui + " " + root).isEqualTo(HomePage.class);
            }
        }
    }

    @Test
    void the_root_pattern_is_the_root_only() {
        assertThat(routes.supportsRoute("/intranet/favoritos", RouteConstants.NO_PARENT_ROUTE)).isFalse();
        assertThat(routes.supportsRoute("/intranetx", RouteConstants.NO_PARENT_ROUTE)).isFalse();
        // Mateu asks about the group on its way to one of its entries: not the root
        assertThat(routes.resolveRoute("/intranet", "/_portal", request("/_portal", "/intranet/favoritos"))).isNull();
    }

    @Test
    void the_configuration_console_keeps_its_own_intranet_entry() {
        assertThat(routes.resolveRoute("/intranet", "/_content-hub", request("/_content-hub", "/intranet"))).isNull();
    }

    @Test
    void the_record_pages_still_resolve() {
        assertThat(routes.resolveRoute("/intranet/noticias/bienvenida", "/_intranet", request("/_intranet",
                "/intranet/noticias/bienvenida"))).isEqualTo(ItemPage.class);
        assertThat(routes.resolveRoute("/intranet/enlace/intranet/m1", "/_intranet", null)).isEqualTo(MenuEntryPage.class);
    }
}
