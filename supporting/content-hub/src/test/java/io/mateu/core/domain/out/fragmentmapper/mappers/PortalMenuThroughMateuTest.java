package io.mateu.core.domain.out.fragmentmapper.mappers;

import io.mateu.core.domain.out.componentmapper.HomeRouteResolver;
import io.mateu.core.domain.out.componentmapper.ReflectionAppMapper;
import io.mateu.core.infra.HeadlessHttpRequest;
import io.mateu.dtos.MenuOptionDto;
import io.mateu.ecdemo1.contenthub.application.menu.MenuLink;
import io.mateu.ecdemo1.contenthub.application.menu.PortalFunction;
import io.mateu.ecdemo1.contenthub.application.menu.ShownEntry;
import io.mateu.ecdemo1.contenthub.application.menu.SiteMenus;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.IntranetHome;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.IntranetMenu;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalMenu;
import io.mateu.uidl.data.FieldLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The portal's dynamic menu through Mateu's own mapping (3.0-beta.40), the way the shells get it: the
 * MenuSupplier field becomes the «Intranet» group, a submenu's entries keep their /intranet/{function}
 * paths on the wire, and a function the menu does not show at its top still resolves its route. In
 * Mateu's package because the wire builder is package-private.
 */
class PortalMenuThroughMateuTest {

    @BeforeEach
    void wire() throws Exception {
        var menus = mock(SiteMenus.class);
        when(menus.shown(any())).thenReturn(List.of(
                new ShownEntry("inicio", "Inicio", new MenuLink.Function("inicio"), List.of()),
                new ShownEntry("rrhh", "RRHH", null, List.of(
                        new ShownEntry("nuevaSolicitud", "Nueva solicitud", new MenuLink.Function("nuevaSolicitud"), List.of()),
                        new ShownEntry("proc", "Procedimiento", new MenuLink.Route("/intranet/noticias/proc"), List.of())))));
        var readers = mock(Readers.class);
        when(readers.of(any(io.mateu.uidl.interfaces.HttpRequest.class)))
                .thenReturn(new Reader("ana", "Ana", EmployeeAttributes.NONE, Set.of(), null));
        var portal = new PortalMenu(menus, siteId -> "/intranet", readers);
        Method register = PortalMenu.class.getDeclaredMethod("register");
        register.setAccessible(true);
        register.invoke(portal);
    }

    static List<String> flatten(List<MenuOptionDto> options, boolean visibleOnly) {
        return options.stream().flatMap(o -> {
            if (visibleOnly && !o.visible()) {
                return java.util.stream.Stream.<String>empty();
            }
            var self = java.util.stream.Stream.of(o.label() + "=" + o.path());
            return o.submenus() == null || o.submenus().isEmpty() ? self
                    : java.util.stream.Stream.concat(self, flatten(o.submenus(), visibleOnly).stream());
        }).toList();
    }

    @Test
    void the_shell_gets_the_configured_menu_with_the_routes_of_always() {
        var request = new HeadlessHttpRequest(null);
        var app = ReflectionAppMapper.mapToAppComponent(new IntranetHome(), "", "/intranet/inicio", "", "x", request);
        var wire = AppMenuDtoBuilder.buildMenu(app, "/intranet/inicio", app.route());
        var visible = flatten(wire, true);
        assertThat(visible).contains("Inicio=/intranet/inicio", "Nueva solicitud=/intranet/nuevaSolicitud",
                "Procedimiento=/intranet/noticias/proc");
        assertThat(visible.stream().filter(v -> v.startsWith("RRHH="))).hasSize(1);
        assertThat(flatten(wire, false)).as("every function still travels, hidden")
                .contains("porAprobar=/intranet/porAprobar", "buscar=/intranet/buscar");

        // a deep link to a function the menu shows only inside a submenu, or not at all, still resolves
        for (var f : List.of("nuevaSolicitud", "porAprobar")) {
            var selected = HomeRouteResolver.getSelectedOption(app.route(), "/intranet/" + f, app.menu(), request);
            assertThat(selected).hasValueSatisfying(a -> {
                assertThat(a).isInstanceOf(FieldLink.class);
                assertThat(((FieldLink) a).fieldName()).isEqualTo(f);
                assertThat(((FieldLink) a).serverSideType()).isEqualTo(IntranetMenu.class.getName());
            });
        }
        assertThat(PortalFunction.values()).hasSize(16);
    }
}
