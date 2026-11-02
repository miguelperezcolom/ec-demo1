package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.RemoteUis;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSourceKind;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.render.MediaUrls;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice.BlockForm;
import io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice.Catalog;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.web.VisualEditorController;
import io.mateu.uidl.data.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A «Componente remoto» in the two editors: the visual editor offers the registry's sources, round-trips
 * the block (source, route, attributes) and refuses one that is not registered or does not fit; the form
 * shows only its fields and converts it the same way.
 */
class VisualEditorRemoteBlocksTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    static final UiSource RIU = new UiSource("riu-class", "Riu Class", UiSourceKind.MATEU_REMOTE, "/_loyalty", null,
            null, null, true);
    static final UiSource PUSH = new UiSource("push", "Avisos", UiSourceKind.WEB_COMPONENT, "/_inbox/push/push.js",
            "ec-push-toggle", null, null, true);

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
        var registry = Map.of("riu-class", RIU, "push", PUSH);
        var sources = new UiSources() {
            @Override
            public Optional<UiSource> find(String id) {
                return Optional.ofNullable(registry.get(id));
            }

            @Override
            public List<UiSource> all() {
                return List.of(RIU, PUSH);
            }
        };
        var commands = new ItemCommands(items, mock(DomainEvents.class), mock(EmployeeDirectory.class), named,
                Clock.fixed(NOW, ZoneOffset.UTC), new RemoteUis(sources, named, ""));
        var renderer = mock(PageRenderer.class);
        when(renderer.draftHtml(any(), any())).thenReturn("<p>drawn</p>");
        var catalog = mock(Catalog.class);
        when(catalog.values(any())).thenReturn(List.of());
        when(catalog.uiSources()).thenReturn(List.of(new Option("riu-class", "Riu Class (UI de Mateu)"),
                new Option("push", "Avisos (Web component)")));
        var controller = new VisualEditorController(new Readers(mock(EmployeeDirectory.class), mock(ContentIndex.class)),
                items, commands, mock(AudienceCommands.class), named, renderer, mock(ContentRenderer.class),
                mock(MediaRepository.class), mock(MediaUrls.class), catalog, mock(io.mateu.ecdemo1.contenthub.application.out.Translator.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        stored.put("p1", Item.create("p1", ItemType.PAGE, "eva", "es", new ItemContent("s1", Audience.EVERYONE,
                Map.of("es", new Translation("Herramientas", "", "")),
                List.of(new Block("r1", BlockType.REMOTE, null, "Socios", null, null, null, null, null, null,
                        RemoteUi.of("riu-class", "/loyalty/members"))),
                null, null, false, false, null, null), NOW.minusSeconds(60)));
    }

    static String token() {
        var enc = Base64.getUrlEncoder().withoutPadding();
        return "Bearer " + enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString("{\"preferred_username\":\"eva\"}".getBytes(StandardCharsets.UTF_8)) + ".x";
    }

    String save(String blocks) throws Exception {
        return mvc.perform(put("/_intranet/api/editor/pages/p1/blocks").header("Authorization", token())
                        .header(Readers.SCOPES_HEADER, "content:edit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[" + blocks + "]}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void the_editor_offers_the_registry_and_reads_the_block_back() throws Exception {
        mvc.perform(get("/_intranet/api/editor/pages/p1").header("Authorization", token())
                        .header(Readers.SCOPES_HEADER, "content:edit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalog.uiSources[0].value").value("riu-class"))
                .andExpect(jsonPath("$.catalog.types[?(@.value=='REMOTE')].fields[0]").value("remoteSourceId"))
                .andExpect(jsonPath("$.blocks[0].type").value("REMOTE"))
                .andExpect(jsonPath("$.blocks[0].remoteSourceId").value("riu-class"))
                .andExpect(jsonPath("$.blocks[0].remoteRoute").value("/loyalty/members"));
    }

    @Test
    void the_editor_saves_a_remote_block_of_a_registered_source() throws Exception {
        save("{\"id\":\"r2\",\"type\":\"REMOTE\",\"title\":\"Avisos\",\"remoteSourceId\":\"push\","
                + "\"remoteAttributes\":{\"mode\":\"compact\"}}");
        var blocks = stored.get("p1").draft().blocks();
        assertThat(blocks).extracting(Block::id).containsExactly("r2");
        assertThat(blocks.get(0).remote()).isEqualTo(new RemoteUi("push", null, Map.of("mode", "compact")));
    }

    @Test
    void an_unregistered_source_a_misfit_or_a_handler_is_refused() throws Exception {
        assertThat(save("{\"id\":\"r2\",\"type\":\"REMOTE\",\"remoteSourceId\":\"evil\",\"remoteRoute\":\"/x\"}"))
                .contains("evil");
        assertThat(save("{\"id\":\"r2\",\"type\":\"REMOTE\",\"remoteSourceId\":\"riu-class\",\"remoteAttributes\":{\"a\":\"b\"}}"))
                .contains("ruta");
        assertThat(save("{\"id\":\"r2\",\"type\":\"REMOTE\",\"remoteSourceId\":\"push\",\"remoteAttributes\":{\"onclick\":\"x\"}}"))
                .contains("onclick");
        assertThat(save("{\"id\":\"r2\",\"type\":\"REMOTE\",\"remoteSourceId\":\"riu-class\",\"remoteRoute\":\"//evil.com\"}"))
                .contains("ruta");
        assertThat(save("{\"id\":\"r2\",\"type\":\"REMOTE\"}")).contains("UI externa");
        assertThat(stored.get("p1").draft().blocks()).extracting(Block::id).containsExactly("r1");
    }

    @Test
    void the_form_shows_only_the_remote_fields_and_converts_the_same_way() {
        assertThat(BlockForm.fieldsOf(BlockType.REMOTE)).containsExactly("remoteSourceId", "remoteRoute", "remoteAttributes");
        assertThat(BlockForm.fieldsOf(BlockType.HTML)).containsExactly("html");
        var f = BlockForm.of(new Block("r", BlockType.REMOTE, null, "x", null, null, null, null, null, null,
                new RemoteUi("push", "/a", Map.of("mode", "compact", "label", "Hola"))), "es");
        assertThat(f.remoteSourceId).isEqualTo("push");
        assertThat(f.remoteAttributes).isEqualTo("label=Hola\nmode=compact");
        assertThat(f.isHidden("remoteRoute", null)).isFalse();
        assertThat(f.isHidden("htmlEs", null)).isTrue();
        assertThat(f.toBlock("es").remote()).isEqualTo(new RemoteUi("push", "/a", Map.of("mode", "compact", "label", "Hola")));
        f.type = BlockType.HTML;
        assertThat(f.isHidden("remoteSourceId", null)).isTrue();
        assertThat(f.toBlock("es").remote()).as("only a remote block keeps a UI").isNull();
    }
}
