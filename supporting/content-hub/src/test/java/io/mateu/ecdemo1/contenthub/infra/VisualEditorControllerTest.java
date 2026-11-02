package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.render.MediaUrls;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice.Catalog;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.web.VisualEditorController;
import io.mateu.uidl.data.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The visual editor's API authorises like the backoffice: the caller from the token the gateway verified,
 * the editorial permissions from its X-User-Scopes; reading for any editorial permission, saving for
 * content:edit; a block's audience only from the directory's departments, hotels and job titles.
 */
class VisualEditorControllerTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    final Map<String, Item> stored = new HashMap<>();
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        var items = mock(ItemRepository.class);
        when(items.find(any())).thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
        doAnswer(i -> stored.put(i.<Item>getArgument(0).id(), i.getArgument(0))).when(items).save(any());
        var named = mock(NamedAudiences.class);
        when(named.policy()).thenReturn(new VisibilityPolicy(id -> null));
        when(named.labels()).thenReturn(Function.identity());
        var commands = new ItemCommands(items, mock(DomainEvents.class), mock(EmployeeDirectory.class), named,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var audiences = mock(AudienceCommands.class);
        when(audiences.allProfiles()).thenReturn(Map.of("h1", new AudienceCommands.Profile("h1",
                new EmployeeAttributes(Map.of(Attribute.HOTEL, "CUN01"), "es"), 4)));
        var renderer = mock(PageRenderer.class);
        when(renderer.draftHtml(any(), any())).thenReturn("<p>drawn</p>");
        var catalog = mock(Catalog.class);
        when(catalog.values(any())).thenAnswer(i -> switch (i.<Attribute>getArgument(0)) {
            case DEPARTMENT -> List.of(new Option("RECEPCION", "RECEPCION"));
            case HOTEL -> List.of(new Option("TFS01", "TFS01"), new Option("CUN01", "CUN01"));
            default -> List.of();
        });
        var controller = new VisualEditorController(new Readers(mock(EmployeeDirectory.class), mock(ContentIndex.class)),
                items, commands, audiences, named, renderer, mock(ContentRenderer.class), mock(MediaRepository.class),
                mock(MediaUrls.class), catalog);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        stored.put("p1", Item.create("p1", ItemType.PAGE, "eva", "es", new ItemContent("s1", Audience.in(Attribute.HOTEL, "TFS01"),
                Map.of("es", new Translation("Recepción", "", "")),
                List.of(new Block("b1", BlockType.HTML, null, "Hola", "<p>hola</p>", null, null, null, null, null)),
                null, null, false, false, null, null), NOW.minusSeconds(60)));
        stored.put("a1", Item.create("a1", ItemType.ARTICLE, "eva", "es", new ItemContent("s1", Audience.EVERYONE,
                Map.of("es", new Translation("Noticia", "", "")), List.of(), null, null, false, false, null, null), NOW));
    }

    static String token(String user, String... realmRoles) {
        var enc = Base64.getUrlEncoder().withoutPadding();
        var roles = realmRoles.length == 0 ? "" : ",\"realm_access\":{\"roles\":[\"" + String.join("\",\"", realmRoles) + "\"]}";
        return "Bearer " + enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(("{\"preferred_username\":\"" + user + "\"" + roles + "}").getBytes(StandardCharsets.UTF_8)) + ".x";
    }

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String scopes) {
        request.header("Authorization", token("ana"));
        return scopes == null ? request : request.header(Readers.SCOPES_HEADER, scopes);
    }

    static String body(String departments, String updatedAt) {
        return "{\"blocks\":[{\"id\":\"b2\",\"type\":\"HTML\",\"title\":\"Nuevo\",\"html\":\"<p>x</p>\",\"departments\":[" + departments
                + "]},{\"id\":\"b1\",\"type\":\"QUICK_LINKS\",\"title\":\"Enlaces\",\"links\":[{\"label\":\"Buscar\",\"url\":\"/intranet/buscar\"}]}]"
                + (updatedAt == null ? "" : ",\"updatedAt\":\"" + updatedAt + "\"") + "}";
    }

    @Test
    void someone_without_an_editorial_permission_gets_nothing() throws Exception {
        mvc.perform(as(get("/_intranet/api/editor/pages/p1"), "")).andExpect(status().isForbidden());
        mvc.perform(as(get("/_intranet/api/editor/pages/p1"), "booking:read")).andExpect(status().isForbidden());
        mvc.perform(as(get("/_intranet/api/editor/visual-editor.js"), "")).andExpect(status().isForbidden());
        // no X-User-Scopes (the gateway could not ask) and no realm admin: nothing either
        mvc.perform(get("/_intranet/api/editor/pages/p1").header("Authorization", token("ana"))).andExpect(status().isForbidden());
        mvc.perform(as(put("/_intranet/api/editor/pages/p1/blocks").contentType(MediaType.APPLICATION_JSON)
                .content(body("", null)), "")).andExpect(status().isForbidden());
        assertThat(stored.get("p1").draft().blocks()).extracting(Block::id).containsExactly("b1");
    }

    @Test
    void an_editor_reads_the_page_with_its_blocks_drawn() throws Exception {
        mvc.perform(as(get("/_intranet/api/editor/pages/p1"), "content:edit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.id").value("p1"))
                .andExpect(jsonPath("$.page.canEdit").value(true))
                .andExpect(jsonPath("$.blocks[0].id").value("b1"))
                .andExpect(jsonPath("$.rendered[0].html").value("<p>drawn</p>"))
                .andExpect(jsonPath("$.catalog.hotels[0].value").value("TFS01"));
        mvc.perform(as(get("/_intranet/api/editor/visual-editor.js"), "content:publish"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ec-visual-editor")));
        mvc.perform(as(get("/_intranet/api/editor/pages/a1"), "content:edit")).andExpect(status().isBadRequest());
        mvc.perform(as(get("/_intranet/api/editor/pages/zz"), "content:edit")).andExpect(status().isNotFound());
    }

    @Test
    void a_realm_admin_without_the_header_is_an_editor_as_in_the_backoffice() throws Exception {
        mvc.perform(get("/_intranet/api/editor/pages/p1").header("Authorization", token("admin", "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void an_editor_saves_the_blocks_and_a_publisher_alone_cannot() throws Exception {
        mvc.perform(as(put("/_intranet/api/editor/pages/p1/blocks").contentType(MediaType.APPLICATION_JSON)
                .content(body("", null)), "content:publish")).andExpect(status().isForbidden());
        mvc.perform(as(put("/_intranet/api/editor/pages/p1/blocks").contentType(MediaType.APPLICATION_JSON)
                .content(body("\"RECEPCION\"", NOW.minusSeconds(60).toString())), "content:edit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        var blocks = stored.get("p1").draft().blocks();
        assertThat(blocks).extracting(Block::id).containsExactly("b2", "b1");
        assertThat(blocks.get(0).audience()).isEqualTo(Audience.in(Attribute.DEPARTMENT, "RECEPCION"));
        assertThat(blocks.get(1).links()).containsExactly(new Block.Link("Buscar", "/intranet/buscar"));
        assertThat(stored.get("p1").draft().audience()).isEqualTo(Audience.in(Attribute.HOTEL, "TFS01"));
    }

    @Test
    void a_value_the_directory_does_not_have_or_a_stale_page_is_refused() throws Exception {
        mvc.perform(as(put("/_intranet/api/editor/pages/p1/blocks").contentType(MediaType.APPLICATION_JSON)
                .content(body("\"DIRECCION\"", null)), "content:edit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("DIRECCION")));
        mvc.perform(as(put("/_intranet/api/editor/pages/p1/blocks").contentType(MediaType.APPLICATION_JSON)
                .content(body("", "2020-01-01T00:00:00Z")), "content:edit"))
                .andExpect(status().isConflict());
        assertThat(stored.get("p1").draft().blocks()).extracting(Block::id).containsExactly("b1");
    }

    @Test
    void ver_como_draws_only_what_the_profile_sees() throws Exception {
        // the profile is in CUN01, the page is for TFS01: it sees no block
        mvc.perform(as(post("/_intranet/api/editor/pages/p1/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"blocks\":[{\"id\":\"b1\",\"type\":\"HTML\",\"html\":\"<p>x</p>\"}],\"viewAs\":\"h1\"}"), "content:edit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageVisible").value(false))
                .andExpect(jsonPath("$.blocks[0].visible").value(false))
                .andExpect(jsonPath("$.blocks[0].html").value(""));
        mvc.perform(as(post("/_intranet/api/editor/pages/p1/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"blocks\":[],\"viewAs\":\"nobody\"}"), "content:edit"))
                .andExpect(status().isBadRequest());
    }
}
