package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.directory.DirectoryUpdates;
import io.mateu.ecdemo1.contenthub.application.directory.EmployeeMessage;
import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.events.ProjectionListener;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.RemoteUis;
import io.mateu.ecdemo1.contenthub.application.usecases.UiSourceCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.application.visibility.Projection;
import io.mateu.ecdemo1.contenthub.application.visibility.ProjectionCache;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.shared.DomainEvent;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSourceKind;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgContentIndex;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgEmployeeDirectory;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgItemRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgSlotRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgUiSourceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The registry of external UIs against a real PostgreSQL: stored and read back, and — what matters — a
 * remote page or a «Componente remoto» block is visible only to whom both its own audience and its
 * source's admit, through the one visibility function; deactivating, narrowing or removing the source
 * hides it at once (the projection is recalculated). Editors can only use registered sources, and only
 * content:admin registers one.
 */
class RemoteUisTest {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcTemplate jdbc;

    static final Reader ADMIN = new Reader("ada", "Ada", EmployeeAttributes.NONE,
            EnumSet.allOf(EditorialPermission.class), null);
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE,
            EnumSet.of(EditorialPermission.EDIT, EditorialPermission.PUBLISH), null);

    static final EmployeeAttributes ANA = profile("TFS01", "CANARIAS", "RECEPCION");
    static final EmployeeAttributes KLAUS = profile("PMI01", "BALEARES", "RECEPCION");

    PgUiSourceRepository sources;
    UiSourceCommands registry;
    RemoteUis remoteUis;
    ItemCommands items;
    Visibility visibility;

    @BeforeAll
    static void start() {
        PG.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        jdbc.execute((java.sql.Connection c) -> {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("schema.sql"));
            return null;
        });
    }

    @AfterAll
    static void stop() {
        PG.stop();
    }

    @BeforeEach
    void wire() {
        for (var t : List.of("ch_item", "ch_ui_source", "ch_named_audience", "ch_employee", "ch_profile", "ch_item_doc",
                "ch_block_doc", "ch_item_media", "ch_projection_state", "ch_chunk")) {
            jdbc.update("delete from " + t);
        }
        var clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
        var itemRepo = new PgItemRepository(jdbc);
        sources = new PgUiSourceRepository(jdbc);
        var employees = new PgEmployeeDirectory(jdbc);
        var index = new PgContentIndex(jdbc);
        var named = new NamedAudiences(new PgNamedAudienceRepository(jdbc), sources);
        var cache = new ProjectionCache();
        var projection = new Projection(index, itemRepo, new PgSlotRepository(jdbc), employees, named, cache);
        var listener = new ProjectionListener(projection, cache);
        var events = new DomainEvents(e -> { }, e -> listener.on((DomainEvent) e));
        visibility = new Visibility(index, projection, clock);
        remoteUis = new RemoteUis(sources, named, "https://wiki.example.com, https://intra.example.com/");
        items = new ItemCommands(itemRepo, events, employees, named, clock, remoteUis);
        registry = new UiSourceCommands(sources, remoteUis, itemRepo, events);
        var directory = new DirectoryUpdates(employees, projection, events);
        directory.apply(employee("ana", "TFS01", "CANARIAS"));
        directory.apply(employee("klaus", "PMI01", "BALEARES"));
    }

    static EmployeeMessage employee(String id, String hotel, String region) {
        return new EmployeeMessage(id + "-" + System.nanoTime(), EmployeeMessage.UPSERTED, id, id, id + "@example.com",
                true, null, null, Map.of("hotel", hotel, "region", region, "department", "RECEPCION", "language", "es"),
                null, null);
    }

    static EmployeeAttributes profile(String hotel, String region, String department) {
        return new EmployeeAttributes(Map.of(Attribute.HOTEL, hotel, Attribute.REGION, region,
                Attribute.DEPARTMENT, department), "es");
    }

    static UiSource riuClass(Audience audience, boolean active) {
        return new UiSource("riu-class", "Riu Class", UiSourceKind.MATEU_REMOTE, "/_loyalty", null, audience,
                Set.of(), active);
    }

    String remotePage() {
        var id = items.create(EDITOR, ItemType.REMOTE_PAGE, "es", new ItemContent(null, Audience.EVERYONE,
                Map.of("es", new Translation("Riu Class", "", "")), List.of(), null, null, false, false, null,
                RemoteUi.of("riu-class", "/loyalty/members").uri()));
        items.publish(EDITOR, id, false);
        return id;
    }

    String pageWithRemoteBlock() {
        var id = items.create(EDITOR, ItemType.PAGE, "es", new ItemContent(null, Audience.EVERYONE,
                Map.of("es", new Translation("Herramientas", "", "")), List.of(
                new Block("intro", BlockType.HTML, null, null, "<p>hola</p>", null, null, null, null, null),
                new Block("riu", BlockType.REMOTE, null, "Acumulaciones", null, null, null, null, null, null,
                        RemoteUi.of("riu-class", "/loyalty/accruals"))), null, null, false, false, null, null));
        items.publish(EDITOR, id, false);
        return id;
    }

    @Test
    void a_source_is_stored_and_read_back_whole() {
        var source = new UiSource("push", "Avisos", UiSourceKind.WEB_COMPONENT, "/_inbox/push/push.js", "ec-push-toggle",
                Audience.allOf(Audience.in(Attribute.REGION, "CANARIAS"), new Audience.Named("jefes")),
                Set.of("content:read", "inbox:read"), false);
        registry.register(ADMIN, source);
        assertThat(sources.find("push")).contains(source);
        assertThat(sources.all()).containsExactly(source);
        assertThatThrownBy(() -> registry.register(ADMIN, source)).isInstanceOf(IllegalArgumentException.class);
        registry.delete(ADMIN, "push");
        assertThat(sources.find("push")).isEmpty();
    }

    @Test
    void only_content_admin_registers_and_an_iframe_only_from_an_allowed_origin() {
        assertThatThrownBy(() -> registry.register(EDITOR, riuClass(null, true))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> registry.register(ADMIN, new UiSource("x", "X", UiSourceKind.IFRAME,
                "https://evil.example.com/", null, null, null, true))).isInstanceOf(IllegalArgumentException.class);
        registry.register(ADMIN, new UiSource("wiki", "Wiki", UiSourceKind.IFRAME, "https://wiki.example.com/app",
                null, null, null, true));
        registry.register(ADMIN, new UiSource("intra", "Intra", UiSourceKind.IFRAME, "https://intra.example.com",
                null, null, null, true));
        assertThat(sources.all()).hasSize(2);
    }

    @Test
    void editors_only_use_registered_sources_in_a_way_that_fits_them() {
        assertThatThrownBy(this::remotePage).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("riu-class");
        registry.register(ADMIN, riuClass(null, true));
        assertThatThrownBy(() -> items.create(EDITOR, ItemType.PAGE, "es", new ItemContent(null, null,
                Map.of("es", new Translation("x", "", "")), List.of(new Block("b", BlockType.REMOTE, null, null, null,
                null, null, null, null, null, new RemoteUi("riu-class", "/x", Map.of("mode", "a")))), null, null, false,
                false, null, null))).as("a Mateu UI takes no attributes").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> items.create(EDITOR, ItemType.LINK, "es", new ItemContent(null, null,
                Map.of("es", new Translation("x", "", "")), List.of(), null, null, false, false, null,
                "ui:riu-class/x"))).as("only a remote page points at a UI").isInstanceOf(IllegalArgumentException.class);
        var page = remotePage();
        var tools = pageWithRemoteBlock();
        assertThat(registry.uses("riu-class")).containsExactlyInAnyOrder(page + " · Riu Class", tools + " · Herramientas");
    }

    @Test
    void a_remote_page_and_block_are_seen_only_by_whom_the_source_also_admits() {
        registry.register(ADMIN, riuClass(Audience.in(Attribute.REGION, "CANARIAS"), true));
        var page = remotePage();
        var tools = pageWithRemoteBlock();

        assertThat(visibility.item(ANA, page)).isPresent();
        assertThat(visibility.item(KLAUS, page)).as("the page's audience is everyone, its source's is not").isEmpty();
        assertThat(visibility.visibleBlockIds(ANA, tools)).containsExactly("intro", "riu");
        assertThat(visibility.visibleBlockIds(KLAUS, tools)).containsExactly("intro");

        // widened: Klaus sees them too, without republishing anything
        registry.update(ADMIN, riuClass(Audience.EVERYONE, true));
        assertThat(visibility.item(KLAUS, page)).isPresent();
        assertThat(visibility.visibleBlockIds(KLAUS, tools)).containsExactly("intro", "riu");

        // deactivated: nobody
        registry.update(ADMIN, riuClass(Audience.EVERYONE, false));
        assertThat(visibility.item(ANA, page)).isEmpty();
        assertThat(visibility.visibleBlockIds(ANA, tools)).containsExactly("intro");

        // removed: nobody either, until an editor points them elsewhere
        registry.update(ADMIN, riuClass(Audience.EVERYONE, true));
        assertThat(visibility.item(ANA, page)).isPresent();
        registry.delete(ADMIN, "riu-class");
        assertThat(visibility.item(ANA, page)).isEmpty();
        assertThat(visibility.visibleBlockIds(ANA, tools)).containsExactly("intro");
    }

    @Test
    void at_the_door_a_reader_also_needs_the_source_s_scopes() {
        registry.register(ADMIN, new UiSource("riu-class", "Riu Class", UiSourceKind.MATEU_REMOTE, "/_loyalty", null,
                Audience.in(Attribute.REGION, "CANARIAS"), Set.of("loyalty:read"), true));
        var ana = new Reader("ana", "Ana", ANA, Set.of(), null, Set.of("loyalty:read"));
        var anaWithout = new Reader("ana", "Ana", ANA, Set.of(), null, Set.of("content:read"));
        var klaus = new Reader("klaus", "Klaus", KLAUS, Set.of(), null, Set.of("loyalty:read"));
        var use = RemoteUi.of("riu-class", "/loyalty/members");
        assertThat(remoteUis.usable(ana, use)).isPresent();
        assertThat(remoteUis.usable(anaWithout, use)).as("no scope").isEmpty();
        assertThat(remoteUis.usable(klaus, use)).as("not its audience").isEmpty();
        assertThat(remoteUis.usable(ana, new RemoteUi("riu-class", "/x", Map.of("a", "b")))).as("does not fit").isEmpty();
        assertThat(remoteUis.usable(ana, RemoteUi.of("other", "/x"))).isEmpty();
    }

    @Test
    void an_iframe_whose_origin_the_deployment_no_longer_allows_is_not_drawn() {
        registry.register(ADMIN, new UiSource("wiki", "Wiki", UiSourceKind.IFRAME, "https://wiki.example.com/app",
                null, null, null, true));
        var ana = new Reader("ana", "Ana", ANA, Set.of(), null, Set.of());
        assertThat(remoteUis.usable(ana, "wiki")).isPresent();
        var stricter = new RemoteUis(sources, new NamedAudiences(new PgNamedAudienceRepository(jdbc), sources), "");
        assertThat(stricter.usable(ana, "wiki")).isEmpty();
    }

    @Test
    void the_port_the_menus_resolve_against() {
        UiSources port = sources;
        registry.register(ADMIN, riuClass(null, true));
        assertThat(port.find("riu-class")).map(UiSource::basePath).contains("/_loyalty");
        assertThat(port.find("nope")).isEmpty();
        assertThat(port.all()).extracting(UiSource::id).containsExactly("riu-class");
        assertThat(UiSources.NONE.all()).isEmpty();
    }
}
