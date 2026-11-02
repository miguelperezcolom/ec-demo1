package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.FakeTranslator;
import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.mateu.ecdemo1.contenthub.infra.VisualEditorControllerTest.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The visual editor per language: the page's blocks come in the language asked for — what a block has no text
 * in shows the default language's, flagged «sin traducir» — and are drawn, previewed, translated (a stubbed
 * translator, never the real AI) and saved in it, the other languages' text kept.
 */
class VisualEditorLanguagesTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    static final String UPDATED = NOW.minusSeconds(60).toString();

    final Map<String, Item> stored = new HashMap<>();
    final FakeTranslator translator = new FakeTranslator();
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
        var renderer = mock(PageRenderer.class);
        // drawn as the block it is given: its title and its links' labels
        when(renderer.draftHtml(any(), any())).thenAnswer(i -> {
            Block b = i.getArgument(1);
            return "<h3>" + b.title() + "</h3>" + b.links().stream().map(Block.Link::label).reduce("", String::concat);
        });
        var controller = new VisualEditorController(new Readers(mock(EmployeeDirectory.class), mock(ContentIndex.class)),
                items, commands, mock(AudienceCommands.class), named, renderer, mock(ContentRenderer.class),
                mock(MediaRepository.class), mock(MediaUrls.class), mock(Catalog.class), translator);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        stored.put("p1", Item.create("p1", ItemType.PAGE, "eva", "es", new ItemContent("s1", Audience.EVERYONE,
                Map.of("es", new Translation("Inicio", "", ""), "en", new Translation("Home", "", "")),
                List.of(new Block("b1", BlockType.HTML, null, "Hola", "<p>hola</p>", null, null, null, null, null)
                                .withText("en", "es", new Block.BlockText("Hello", "<p>hello</p>", null)),
                        new Block("b2", BlockType.QUICK_LINKS, null, "Enlaces", null, null, null, null,
                                List.of(new Block.Link("Buscar", "/intranet/buscar")), null)),
                null, null, false, false, null, null), NOW.minusSeconds(60)));
    }

    Block block(String id) {
        return stored.get("p1").draft().blocks().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void the_page_comes_in_the_language_asked_for_with_the_untranslated_flagged() throws Exception {
        mvc.perform(as(get("/_intranet/api/editor/pages/p1?language=en"), "content:edit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.title").value("Home"))
                .andExpect(jsonPath("$.page.language").value("en"))
                .andExpect(jsonPath("$.page.defaultLanguage").value("es"))
                .andExpect(jsonPath("$.page.languages[2]").value("de"))
                .andExpect(jsonPath("$.page.canTranslate").value(true))
                .andExpect(jsonPath("$.blocks[0].title").value("Hello"))
                .andExpect(jsonPath("$.blocks[0].html").value("<p>hello</p>"))
                .andExpect(jsonPath("$.blocks[0].untranslated").value(false))
                .andExpect(jsonPath("$.blocks[1].title").value("Enlaces"))
                .andExpect(jsonPath("$.blocks[1].untranslated").value(true))
                .andExpect(jsonPath("$.rendered[0].html").value("<h3>Hello</h3>"));
        // no language: the default one
        mvc.perform(as(get("/_intranet/api/editor/pages/p1"), "content:edit"))
                .andExpect(jsonPath("$.page.language").value("es"))
                .andExpect(jsonPath("$.blocks[0].title").value("Hola"))
                .andExpect(jsonPath("$.blocks[0].untranslated").value(false));
        mvc.perform(as(get("/_intranet/api/editor/pages/p1?language=fr"), "content:edit")).andExpect(status().isBadRequest());
    }

    static String english(String b2Title, String b2Label) {
        return "{\"language\":\"en\",\"updatedAt\":\"" + UPDATED + "\",\"blocks\":["
                + "{\"id\":\"b1\",\"type\":\"HTML\",\"title\":\"Hello there\",\"html\":\"<p>hello</p>\"},"
                + "{\"id\":\"b2\",\"type\":\"QUICK_LINKS\",\"title\":\"" + b2Title + "\",\"links\":[{\"label\":\"" + b2Label
                + "\",\"url\":\"/intranet/buscar\"}]}]}";
    }

    @Test
    void saving_in_a_language_writes_only_that_languages_text() throws Exception {
        // b1's English title changed; b2 left as shown (the Spanish fallback)
        mvc.perform(as(put("/_intranet/api/editor/pages/p1/blocks").contentType(MediaType.APPLICATION_JSON)
                        .content(english("Enlaces", "Buscar")), "content:edit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.language").value("en"))
                .andExpect(jsonPath("$.blocks[1].untranslated").value(true));
        assertThat(block("b1").title()).as("the Spanish text stays").isEqualTo("Hola");
        assertThat(block("b1").in("en", "es").title()).isEqualTo("Hello there");
        assertThat(block("b2").translations()).as("left as shown: still untranslated").isEmpty();
        assertThat(block("b2").links()).containsExactly(new Block.Link("Buscar", "/intranet/buscar"));
    }

    @Test
    void the_preview_draws_the_unsaved_blocks_in_the_language_edited() throws Exception {
        mvc.perform(as(post("/_intranet/api/editor/pages/p1/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(english("Links", "Search")), "content:edit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].html").value("<h3>Hello there</h3>"))
                .andExpect(jsonPath("$.blocks[1].html").value("<h3>Links</h3>Search"));
        assertThat(block("b2").translations()).as("a preview saves nothing").isEmpty();
    }

    @Test
    void traducir_con_ia_fills_what_the_language_lacks_for_review_without_saving() throws Exception {
        mvc.perform(as(post("/_intranet/api/editor/pages/p1/translate").contentType(MediaType.APPLICATION_JSON)
                        .content(english("Enlaces", "Buscar")), "content:edit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translated").value(2))
                .andExpect(jsonPath("$.blocks[0].title").value("Hello there"))
                .andExpect(jsonPath("$.blocks[1].title").value("[en] Enlaces"))
                .andExpect(jsonPath("$.blocks[1].links[0].label").value("[en] Buscar"))
                .andExpect(jsonPath("$.blocks[1].untranslated").value(false))
                .andExpect(jsonPath("$.rendered[1].html").value("<h3>[en] Enlaces</h3>[en] Buscar"));
        assertThat(translator.languages).containsExactly("en");
        assertThat(block("b2").translations()).as("nothing saved: the editor reviews it").isEmpty();
        // in the default language there is nothing to translate into; a reader-only editor cannot
        mvc.perform(as(post("/_intranet/api/editor/pages/p1/translate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"blocks\":[]}"), "content:edit")).andExpect(status().isBadRequest());
        mvc.perform(as(post("/_intranet/api/editor/pages/p1/translate").contentType(MediaType.APPLICATION_JSON)
                .content(english("Enlaces", "Buscar")), "content:publish")).andExpect(status().isForbidden());
    }
}
