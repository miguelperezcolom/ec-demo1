package io.mateu.ecdemo1.contenthub.application.menu;

import io.mateu.ecdemo1.contenthub.application.out.MenuRepository;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSourceKind;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuEntry;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The menus service: the seed, owner scoping, permissions, and what each reader is shown. */
class SiteMenusTest {

    static final Reader ADMIN = new Reader("ada", "Ada", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.ADMIN), null);
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.EDIT), null);
    static final Reader RECEPTION = new Reader("ana", "Ana",
            new EmployeeAttributes(Map.of(Attribute.DEPARTMENT, "RECEPCION"), "en"), Set.of(), null);
    static final Reader KITCHEN = new Reader("kiko", "Kiko",
            new EmployeeAttributes(Map.of(Attribute.DEPARTMENT, "COCINA"), "es"), Set.of(), null);

    /** The store, in memory, as documents — so what is saved goes through the JSON a real one writes. */
    static class MemoryMenus implements MenuRepository {
        final Map<String, String> docs = new TreeMap<>();

        @Override
        public Optional<SiteMenu> find(String siteId) {
            return Optional.ofNullable(docs.get(siteId)).map(d -> Json.read(d, SiteMenu.class));
        }

        @Override
        public void save(SiteMenu menu) {
            docs.put(menu.siteId(), Json.write(menu));
        }

        @Override
        public boolean saveIfAbsent(SiteMenu menu) {
            return docs.putIfAbsent(menu.siteId(), Json.write(menu)) == null;
        }

        @Override
        public List<String> siteIds() {
            return new ArrayList<>(docs.keySet());
        }
    }

    /** Pages: only "visible-page" can be seen. */
    static class FakePages implements MenuTargetResolver {
        public String kind() {
            return MenuTarget.PAGE;
        }

        public String label() {
            return "Página";
        }

        public Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
            return "visible-page".equals(target.value())
                    ? Optional.of(new MenuLink.Route("/intranet/noticias/" + target.value())) : Optional.empty();
        }

        public List<Choice> choices(String siteId) {
            return List.of();
        }
    }

    /** What a newsletter site type would add: its archive, for newsletter sites only. */
    static class NewsletterArchive implements MenuTargetResolver {
        public String kind() {
            return "NEWSLETTER_ARCHIVE";
        }

        public String label() {
            return "Archivo";
        }

        public boolean appliesTo(String siteId) {
            return siteId.startsWith("newsletter-");
        }

        public Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
            return Optional.of(new MenuLink.Route("/n/" + context.siteId() + "/archivo"));
        }

        public List<Choice> choices(String siteId) {
            return List.of();
        }
    }

    MemoryMenus store;
    SiteMenus menus;
    UiSource usersUi = new UiSource("users", "Usuarios", UiSourceKind.MATEU_REMOTE, "/_users",
            null, null, Set.of("users:admin"), true);

    @BeforeEach
    void setUp() {
        store = new MemoryMenus();
        var named = mock(NamedAudiences.class);
        when(named.policy()).thenReturn(new VisibilityPolicy(id -> null));
        var sources = mock(UiSources.class);
        when(sources.find("users")).thenReturn(Optional.of(usersUi));
        menus = new SiteMenus(store, named, List.of(new FakePages(), new StandardTargets.Builtins(),
                new StandardTargets.Urls(), new StandardTargets.Remotes(sources, named), new NewsletterArchive()));
    }

    List<ShownEntry> shown(String site, Reader reader, Set<String> scopes) {
        return menus.shown(new MenuContext(site, reader, scopes));
    }

    static List<String> labels(List<ShownEntry> entries) {
        return entries.stream().map(ShownEntry::label).toList();
    }

    @Test
    void the_intranet_starts_with_exactly_the_portal_menu_it_had() {
        var menu = menus.menu(SiteMenu.INTRANET);
        assertThat(menu.entries()).extracting(MenuEntry::id)
                .containsExactlyElementsOf(Arrays.stream(PortalFunction.values()).map(PortalFunction::key).toList());
        assertThat(labels(shown(SiteMenu.INTRANET, KITCHEN, Set.of()))).containsExactly("Inicio", "Noticias", "Avisos",
                "Documentos", "Secciones", "Personas", "Organigrama", "Buscar", "Documentos de Drive", "Mis favoritos",
                "Pendientes", "Nueva solicitud", "Mis solicitudes", "Por aprobar", "Encuestas", "Mis preferencias");
        assertThat(shown(SiteMenu.INTRANET, KITCHEN, Set.of())).allSatisfy(e ->
                assertThat(e.link()).isEqualTo(new MenuLink.Function(e.id())));
    }

    @Test
    void the_seed_is_stored_once_and_never_over_an_administrators_menu() {
        assertThat(menus.seedIfAbsent(DefaultMenus.intranet())).isTrue();
        menus.remove(ADMIN, SiteMenu.INTRANET, "organigrama");
        assertThat(menus.seedIfAbsent(DefaultMenus.intranet())).isFalse();
        assertThat(menus.menu(SiteMenu.INTRANET).find("organigrama")).isEmpty();
        assertThat(menus.menu(SiteMenu.INTRANET).entries()).hasSize(PortalFunction.values().length - 1);
    }

    @Test
    void each_site_owns_its_menu() {
        var site = "newsletter-n1";
        assertThat(menus.menu(site).isEmpty()).as("a new site starts with none").isTrue();
        var archive = menus.add(ADMIN, site, null, Map.of("es", "Archivo"), new MenuTarget("NEWSLETTER_ARCHIVE", "n1"),
                null, true);
        menus.add(ADMIN, site, null, Map.of("es", "Buscar"), MenuTarget.builtin("buscar"), null, true);

        assertThat(menus.sites()).containsExactly(SiteMenu.INTRANET, site);
        assertThat(menus.menu(site).entries()).hasSize(2);
        assertThat(menus.menu(SiteMenu.INTRANET).entries()).hasSize(PortalFunction.values().length);
        assertThat(shown(site, KITCHEN, Set.of()).getFirst().link()).isEqualTo(new MenuLink.Route("/n/newsletter-n1/archivo"));
        assertThat(menus.targetKinds(site)).extracting(MenuTargetResolver::kind).contains("NEWSLETTER_ARCHIVE");
        assertThat(menus.targetKinds(SiteMenu.INTRANET)).extracting(MenuTargetResolver::kind)
                .doesNotContain("NEWSLETTER_ARCHIVE");

        // the newsletter's kind means nothing on the intranet: such an entry is not shown there
        menus.add(ADMIN, SiteMenu.INTRANET, null, Map.of("es", "Archivo"), new MenuTarget("NEWSLETTER_ARCHIVE", "n1"),
                null, true);
        assertThat(labels(shown(SiteMenu.INTRANET, KITCHEN, Set.of()))).doesNotContain("Archivo");
        assertThat(archive).isNotBlank();
    }

    @Test
    void only_content_admin_configures_menus() {
        assertThatThrownBy(() -> menus.remove(EDITOR, SiteMenu.INTRANET, "buscar")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> menus.add(EDITOR, "newsletter-n1", null, Map.of("es", "X"), MenuTarget.builtin("buscar"),
                null, true)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> menus.add(ADMIN, SiteMenu.INTRANET, null, Map.of("es", "X"), new MenuTarget("WHATEVER", "x"),
                null, true)).hasMessageContaining("desconocido");
    }

    @Test
    void each_reader_gets_what_their_audience_and_the_targets_let_through() {
        var site = SiteMenu.INTRANET;
        menus.reset(ADMIN, site);
        var desk = menus.add(ADMIN, site, null, Map.of("es", "Recepción", "en", "Front desk"), null,
                Audience.in(Attribute.DEPARTMENT, "RECEPCION"), true);
        menus.add(ADMIN, site, desk, Map.of("es", "Procedimiento", "en", "Procedure"), MenuTarget.page("visible-page"), null, true);
        menus.add(ADMIN, site, desk, Map.of("es", "Borrador"), MenuTarget.page("hidden-page"), null, true);
        var empty = menus.add(ADMIN, site, null, Map.of("es", "Vacío"), null, null, true);
        menus.add(ADMIN, site, empty, Map.of("es", "Oculta"), MenuTarget.page("hidden-page"), null, true);
        menus.add(ADMIN, site, null, Map.of("es", "Web de Riu"), MenuTarget.url("https://www.riu.com"), null, true);
        menus.add(ADMIN, site, null, Map.of("es", "Usuarios"), MenuTarget.remote("users", "/people"), null, true);
        menus.add(ADMIN, site, null, Map.of("es", "Otra"), MenuTarget.remote("ghost", ""), null, true);

        var forReception = shown(site, RECEPTION, Set.of("users:admin"));
        var deskEntry = forReception.stream().filter(e -> e.id().equals(desk)).findFirst().orElseThrow();
        assertThat(deskEntry.label()).as("in the reader's language").isEqualTo("Front desk");
        assertThat(labels(deskEntry.children())).as("the page they cannot see is left out").containsExactly("Procedure");
        assertThat(labels(forReception)).as("a submenu with nothing visible is dropped").doesNotContain("Vacío")
                .contains("Web de Riu", "Usuarios").doesNotContain("Otra");
        assertThat(forReception.stream().filter(e -> e.label().equals("Web de Riu")).findFirst().orElseThrow().link())
                .isEqualTo(new MenuLink.External("https://www.riu.com"));
        assertThat(forReception.stream().filter(e -> e.label().equals("Usuarios")).findFirst().orElseThrow().link())
                .isEqualTo(new MenuLink.Remote(usersUi, "/people"));

        var forKitchen = shown(site, KITCHEN, Set.of());
        assertThat(labels(forKitchen)).as("not their audience; no scope for the users UI")
                .doesNotContain("Recepción", "Front desk", "Usuarios").contains("Inicio", "Web de Riu");
        assertThat(menus.shownEntry(new MenuContext(site, KITCHEN, Set.of()), desk)).isEmpty();
    }
}
