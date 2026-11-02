package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterRenderer;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.application.usecases.RemoteUis;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.render.RemoteUiRenderer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Blocks are drawn in the reader's language with an item's fallback, part by part: on the portal (a page, a
 * newsletter site's page in the site's language) and in a newsletter issue, web and e-mail, in each group's.
 */
class BlockLanguagesRenderingTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    static final Block WELCOME = new Block("w", BlockType.HTML, null, "Bienvenida", "<p>Hola a todos</p>", null, null,
            null, null, null).withText("en", "es", new Block.BlockText("Welcome", "<p>Hello everyone</p>", null));
    static final Block LINKS = new Block("l", BlockType.QUICK_LINKS, null, "Enlaces", null, null, null, null,
            List.of(new Block.Link("Buscar", "/intranet/buscar"), new Block.Link("Nóminas", "https://rrhh.example.com")), null)
            .withText("en", "es", new Block.BlockText(null, null, Map.of("/intranet/buscar", "Search")));

    static Reader reader(String language) {
        return new Reader("ana", "Ana", new EmployeeAttributes(Map.of(Attribute.HOTEL, "TFS01"), language), Set.of(), null);
    }

    PageRenderer renderer() {
        var visibility = mock(Visibility.class);
        when(visibility.visibleBlockIds(any(), eq("p1"))).thenReturn(List.of("w", "l"));
        var items = mock(ItemRepository.class);
        var page = Item.create("p1", ItemType.PAGE, "eva", "es", new ItemContent(null, null,
                Map.of("es", new Translation("Inicio", "", "")), List.of(WELCOME, LINKS), null, null, false, false, null, null), NOW);
        page.publish("eva", true, false, NOW);
        when(items.find("p1")).thenReturn(Optional.of(page));
        var content = mock(ContentRenderer.class);
        when(content.body(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        return new PageRenderer(visibility, items, content, mock(ReaderActions.class), mock(RemoteUis.class),
                mock(RemoteUiRenderer.class));
    }

    @Test
    void the_portal_draws_a_pages_blocks_in_the_readers_language_or_the_pages_default() {
        var pages = renderer();
        var en = pages.visibleBlocks(reader("en"), "p1");
        assertThat(en).extracting(Block::title).containsExactly("Welcome", "Enlaces");
        assertThat(en.get(1).links()).extracting(Block.Link::label).containsExactly("Search", "Nóminas");
        assertThat(pages.blockHtml(reader("en"), en.getFirst(), "h")).contains("Welcome", "Hello everyone").doesNotContain("Hola");
        // a language the page has no block text in: the default one
        assertThat(pages.visibleBlocks(reader("de"), "p1")).extracting(Block::title).containsExactly("Bienvenida", "Enlaces");
        assertThat(pages.visibleBlocks(reader("es"), "p1")).extracting(Block::title).containsExactly("Bienvenida", "Enlaces");
        // a newsletter site's page: the site's language for the reader, whatever theirs
        assertThat(pages.visibleBlocks(reader("de"), "p1", "en")).extracting(Block::title).containsExactly("Welcome", "Enlaces");
    }

    static NewsletterRenderer.Links links() {
        return new NewsletterRenderer.Links() {
            public String item(VisibleItem item) {
                return "/i/" + item.itemId();
            }

            public String rich(String html) {
                return html;
            }

            public String medium(String mediaId) {
                return null;
            }

            public String permalink() {
                return "/p";
            }

            public String unsubscribe() {
                return "/u";
            }

            public String logo() {
                return null;
            }
        };
    }

    static String render(Site site, NewsletterIssue issue, NewsletterRenderer.Mode mode, String language) {
        return NewsletterRenderer.render(new NewsletterRenderer.View(site, issue, mode, language, Map.of(), b -> List.of(),
                b -> true, Map.of(), "Hola", links()));
    }

    @Test
    void a_newsletter_issue_is_drawn_in_each_groups_language_web_and_email() {
        var site = Site.create("boletin", Site.Kind.NEWSLETTER, new Site.Definition("Boletín", "", null,
                List.of("es", "en"), null, null, null, null, true));
        var newsletter = Newsletter.create("boletin", new Newsletter.Settings(List.of(WELCOME, LINKS), null, null, false), NOW);
        var issue = newsletter.openIssue("Boletín", null, Map.of(), NOW);
        for (var mode : NewsletterRenderer.Mode.values()) {
            assertThat(render(site, issue, mode, "en")).as(mode.name())
                    .contains("Welcome", "Hello everyone", ">Search<", ">Nóminas<", "Enlaces")
                    .doesNotContain("Hola a todos", "Bienvenida");
            assertThat(render(site, issue, mode, "es")).as(mode.name())
                    .contains("Bienvenida", "Hola a todos", ">Buscar<").doesNotContain("Welcome");
        }
        // a site first written in English: its blocks' own text is English, a Spanish group gets its translation
        var english = Site.create("bulletin", Site.Kind.NEWSLETTER, new Site.Definition("Bulletin", "", null,
                List.of("en", "es"), null, null, null, null, true));
        assertThat(english.defaultLanguage()).isEqualTo("en");
    }
}
