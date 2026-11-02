package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.menu.MenuLink;
import io.mateu.ecdemo1.contenthub.application.menu.PortalFunction;
import io.mateu.ecdemo1.contenthub.application.menu.ShownEntry;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.uidl.data.FieldLink;
import io.mateu.uidl.data.Menu;
import io.mateu.uidl.data.RemoteMenu;
import io.mateu.uidl.data.RouteLink;
import io.mateu.uidl.interfaces.Actionable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** What the portal's MenuSupplier answers Mateu: the shown menu as entries, with every route kept. */
class PortalMenuTest {

    final PortalMenu portal = new PortalMenu(null, siteId -> "/intranet", null);

    static ShownEntry function(String key, String label) {
        return new ShownEntry(key, label, new MenuLink.Function(key), List.of());
    }

    static List<Actionable> visible(List<Actionable> entries) {
        return entries.stream().filter(a -> !a.hidden()).toList();
    }

    @Test
    void every_portal_function_is_a_routed_field_of_the_portal_menu() {
        assertThat(PortalMenu.routedFields(IntranetMenu.class))
                .containsExactlyElementsOf(Arrays.stream(PortalFunction.values()).map(PortalFunction::key).toList());
    }

    @Test
    void the_seeded_menu_makes_the_same_entries_the_menu_fields_made() {
        var shown = Arrays.stream(PortalFunction.values()).map(f -> function(f.key(), f.label())).toList();
        var entries = portal.actionables("intranet", IntranetMenu.class, shown);
        assertThat(entries).hasSize(PortalFunction.values().length).allMatch(a -> !a.hidden());
        var noticias = (FieldLink) entries.get(1);
        assertThat(noticias.path()).isEqualTo("/noticias");
        assertThat(noticias.label()).isEqualTo("Noticias");
        assertThat(noticias.fieldName()).isEqualTo("noticias");
        assertThat(noticias.serverSideType()).isEqualTo(IntranetMenu.class.getName());
    }

    @Test
    void what_the_menu_does_not_show_at_its_top_stays_routed_but_hidden() {
        var shown = List.of(
                function("inicio", "Home"),
                new ShownEntry("rrhh", "RRHH", null, List.of(function("nuevaSolicitud", "Nueva"))));
        var entries = portal.actionables("intranet", IntranetMenu.class, shown);

        assertThat(visible(entries)).hasSize(2);
        var group = (Menu) visible(entries).get(1);
        assertThat(group.path()).as("no path of its own: its entries keep their routes").isNull();
        assertThat(group.submenu()).singleElement().satisfies(a -> assertThat(a.path()).isEqualTo("/nuevaSolicitud"));

        var hidden = entries.stream().filter(Actionable::hidden).map(Actionable::path).toList();
        assertThat(hidden).as("every function but the one at the top, routed")
                .hasSize(PortalFunction.values().length - 1).contains("/nuevaSolicitud", "/porAprobar")
                .doesNotContain("/inicio");
    }

    @Test
    void pages_links_and_other_uis_go_where_the_existing_routes_are() {
        var users = new UiSources.UiSource("users", "Usuarios", UiSources.Kind.MATEU_REMOTE, "/_users", null, Set.of());
        var frame = new UiSources.UiSource("bi", "BI", UiSources.Kind.IFRAME, "/_bi", null, Set.of());
        var shown = List.of(
                new ShownEntry("p", "Procedimiento", new MenuLink.Route("/intranet/noticias/proc"), List.of()),
                new ShownEntry("ext", "Riu", new MenuLink.External("https://www.riu.com"), List.of()),
                new ShownEntry("u", "Usuarios", new MenuLink.Remote(users, "/people"), List.of()),
                new ShownEntry("b", "BI", new MenuLink.Remote(frame, ""), List.of()));
        var entries = visible(portal.actionables("intranet", IntranetMenu.class, shown));

        assertThat(((RouteLink) entries.get(0)).route()).isEqualTo("/intranet/noticias/proc");
        assertThat(((RouteLink) entries.get(1)).route()).as("a page that opens it in a new tab")
                .isEqualTo("/intranet/enlace/intranet/ext");
        var remote = (RemoteMenu) entries.get(2);
        assertThat(remote.baseUrl()).isEqualTo("/_users");
        assertThat(remote.route()).isEqualTo("/people");
        assertThat(remote.label()).isEqualTo("Usuarios");
        assertThat(((RouteLink) entries.get(3)).route()).as("hosted in a page of the portal")
                .isEqualTo("/intranet/ui/intranet/b");
        assertThat(PortalRoutes.PAGES.entrySet().stream()
                .filter(e -> "/intranet/enlace/intranet/ext".matches(e.getKey())).map(java.util.Map.Entry::getValue))
                .containsExactly(MenuEntryPage.class);
    }
}
