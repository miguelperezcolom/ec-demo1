package io.mateu.ecdemo1.contenthub.domain.menu;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The menu tree: its operations keep it ordered and acyclic, and what a reader is shown of it. */
class SiteMenuTest {

    SiteMenu menu;

    @BeforeEach
    void setUp() {
        menu = SiteMenu.empty("intranet");
        menu.add("inicio", null, Map.of("es", "Inicio", "en", "Home"), MenuTarget.builtin("inicio"), null, true);
        menu.add("rrhh", null, Map.of("es", "RRHH"), null, null, true);
        menu.add("nueva", "rrhh", Map.of("es", "Nueva solicitud"), MenuTarget.builtin("nuevaSolicitud"), null, true);
        menu.add("mias", "rrhh", Map.of("es", "Mis solicitudes"), MenuTarget.builtin("solicitudes"), null, true);
        menu.add("buscar", null, Map.of("es", "Buscar"), MenuTarget.builtin("buscar"), null, true);
    }

    static List<String> ids(List<MenuNode> nodes) {
        return nodes.stream().map(MenuNode::id).toList();
    }

    @Test
    void entries_come_depth_first_in_order() {
        assertThat(menu.entries()).extracting(MenuEntry::id).containsExactly("inicio", "rrhh", "nueva", "mias", "buscar");
        assertThat(menu.depth("mias")).isEqualTo(1);
    }

    @Test
    void moving_renumbers_both_sets_of_siblings() {
        menu.move("buscar", "rrhh", 0);
        assertThat(menu.children("rrhh")).extracting(MenuEntry::id).containsExactly("buscar", "nueva", "mias");
        assertThat(menu.children("rrhh")).extracting(MenuEntry::order).containsExactly(0, 1, 2);
        assertThat(menu.children(null)).extracting(MenuEntry::id).containsExactly("inicio", "rrhh");
        assertThat(menu.children(null)).extracting(MenuEntry::order).containsExactly(0, 1);
    }

    @Test
    void up_down_indent_and_outdent() {
        menu.moveUp("buscar");
        assertThat(menu.children(null)).extracting(MenuEntry::id).containsExactly("inicio", "buscar", "rrhh");
        menu.moveDown("buscar");
        assertThat(menu.children(null)).extracting(MenuEntry::id).containsExactly("inicio", "rrhh", "buscar");
        menu.indent("buscar"); // the submenu above it
        assertThat(menu.children("rrhh")).extracting(MenuEntry::id).containsExactly("nueva", "mias", "buscar");
        menu.outdent("nueva"); // right after its submenu
        assertThat(menu.children(null)).extracting(MenuEntry::id).containsExactly("inicio", "rrhh", "nueva");
        assertThatThrownBy(() -> menu.indent("rrhh")).as("the entry above is a link")
                .hasMessageContaining("no es un submenú");
        assertThatThrownBy(() -> menu.outdent("inicio")).hasMessageContaining("primer nivel");
    }

    @Test
    void nothing_hangs_from_a_link_and_nothing_inside_itself() {
        assertThatThrownBy(() -> menu.add("x", "inicio", Map.of("es", "X"), MenuTarget.builtin("buscar"), null, true))
                .hasMessageContaining("es un enlace");
        menu.add("sub", "rrhh", Map.of("es", "Sub"), null, null, true);
        assertThatThrownBy(() -> menu.move("rrhh", "sub", 0)).hasMessageContaining("dentro de sí misma");
        assertThatThrownBy(() -> menu.update("rrhh", Map.of("es", "RRHH"), MenuTarget.builtin("buscar"), null, true))
                .as("a submenu with entries stays one").hasMessageContaining("sigue siendo un submenú");
    }

    @Test
    void every_entry_needs_its_default_language_label_and_a_unique_id() {
        assertThatThrownBy(() -> menu.add("y", null, Map.of("en", "Only English"), MenuTarget.builtin("buscar"), null, true))
                .hasMessageContaining("Falta el texto en es");
        assertThatThrownBy(() -> menu.add("inicio", null, Map.of("es", "Otra"), MenuTarget.builtin("buscar"), null, true))
                .hasMessageContaining("Ya hay una entrada");
    }

    @Test
    void removing_a_submenu_takes_what_it_holds() {
        menu.remove("rrhh");
        assertThat(menu.entries()).extracting(MenuEntry::id).containsExactly("inicio", "buscar");
        assertThat(menu.children(null)).extracting(MenuEntry::order).containsExactly(0, 1);
    }

    @Test
    void the_shown_menu_leaves_out_what_the_reader_may_not_see_and_empty_submenus() {
        var hidden = Set.of("nueva", "mias");
        var shown = menu.visible(e -> !hidden.contains(e.id()), "es");
        assertThat(ids(shown)).as("RRHH has nothing left in it").containsExactly("inicio", "buscar");

        var partly = menu.visible(e -> !"mias".equals(e.id()), "es");
        assertThat(ids(partly)).containsExactly("inicio", "rrhh", "buscar");
        assertThat(ids(partly.get(1).children())).containsExactly("nueva");
    }

    @Test
    void an_inactive_entry_is_shown_to_nobody_nor_what_hangs_from_it() {
        menu.update("rrhh", Map.of("es", "RRHH"), null, Audience.EVERYONE, false);
        assertThat(ids(menu.visible(e -> true, "es"))).containsExactly("inicio", "buscar");
        menu.update("buscar", Map.of("es", "Buscar"), MenuTarget.builtin("buscar"), null, false);
        assertThat(ids(menu.visible(e -> true, "es"))).containsExactly("inicio");
    }

    @Test
    void a_submenu_the_reader_may_not_see_hides_what_it_holds() {
        assertThat(ids(menu.visible(e -> !"rrhh".equals(e.id()), "es"))).containsExactly("inicio", "buscar");
    }

    @Test
    void labels_fall_back_to_the_default_language() {
        var en = menu.visible(e -> true, "en");
        assertThat(en.getFirst().label()).isEqualTo("Home");
        assertThat(en.get(2).label()).as("no English label: the Spanish one").isEqualTo("Buscar");
        assertThat(menu.visible(e -> true, "de").getFirst().label()).isEqualTo("Inicio");
    }

    @Test
    void targets_are_checked_where_they_are_made() {
        assertThatThrownBy(() -> MenuTarget.url("intranet/x")).hasMessageContaining("ruta del portal");
        assertThat(MenuTarget.isExternal("https://riu.com")).isTrue();
        var remote = MenuTarget.remote("users", "people/42");
        assertThat(remote.value()).isEqualTo("users/people/42");
        assertThat(remote.remoteSource()).isEqualTo("users");
        assertThat(remote.remoteRoute()).isEqualTo("/people/42");
        assertThat(MenuTarget.remote("crs", null).remoteRoute()).isEmpty();
        assertThat(new MenuTarget("newsletter_archive", "n1").kind()).as("an extension's kind")
                .isEqualTo("NEWSLETTER_ARCHIVE");
    }
}
