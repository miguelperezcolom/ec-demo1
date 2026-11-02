package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.application.usecases.RemoteUis;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSourceKind;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.render.RemoteUiRenderer;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.EmbeddedChrome;
import io.mateu.uidl.data.MicroFrontend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A «Componente remoto» wherever the intranet draws it: in the portal, live — a Mateu UI federated as the
 * shells do (a MicroFrontend on its gateway prefix), a web component or an iframe through
 * {@code <ec-ui-source>} — and only for a reader who may use its source; in the visual editor, a card
 * saying what will be there; in an e-mail, a link to see it on the web.
 */
class RemoteBlockRenderingTest {

    static final UiSource RIU = new UiSource("riu-class", "Riu Class", UiSourceKind.MATEU_REMOTE, "/_loyalty", null,
            Audience.in(Attribute.REGION, "CANARIAS"), Set.of(), true);
    static final UiSource PUSH = new UiSource("push", "Avisos <del navegador>", UiSourceKind.WEB_COMPONENT,
            "/_inbox/push/push.js", "ec-push-toggle", null, Set.of("inbox:read"), true);
    static final UiSource WIKI = new UiSource("wiki", "Wiki", UiSourceKind.IFRAME, "https://wiki.example.com/app",
            null, null, Set.of(), true);
    static final UiSource OFF = new UiSource("off", "Apagada", UiSourceKind.MATEU_REMOTE, "/_off", null, null, Set.of(), false);

    static final Map<String, UiSource> REGISTRY = Map.of("riu-class", RIU, "push", PUSH, "wiki", WIKI, "off", OFF);

    final UiSources sources = new UiSources() {
        @Override
        public Optional<UiSource> find(String id) {
            return Optional.ofNullable(REGISTRY.get(id));
        }

        @Override
        public List<UiSource> all() {
            return List.copyOf(REGISTRY.values());
        }
    };

    final NamedAudiences named = new NamedAudiences(mock(NamedAudienceRepository.class), sources);
    final RemoteUis remoteUis = new RemoteUis(sources, named, "https://wiki.example.com");
    final RemoteUiRenderer remote = new RemoteUiRenderer(sources);
    final PageRenderer renderer = new PageRenderer(mock(Visibility.class), mock(ItemRepository.class),
            mock(ContentRenderer.class), mock(ReaderActions.class), remoteUis, remote);

    static Reader reader(String region, String... scopes) {
        return new Reader("ana", "Ana", new EmployeeAttributes(Map.of(Attribute.REGION, region), "es"), Set.of(), null,
                Set.of(scopes));
    }

    static Block block(String source, String route, Map<String, String> attrs) {
        return new Block("b1", BlockType.REMOTE, null, "Socios", null, null, null, null, null, null,
                new RemoteUi(source, route, attrs));
    }

    @Test
    void a_mateu_ui_is_federated_as_the_shells_do_it() {
        var c = renderer.content(reader("CANARIAS"), block("riu-class", "/loyalty/members", Map.of()));
        assertThat(c).isInstanceOf(MicroFrontend.class);
        var mf = (MicroFrontend) c;
        assertThat(mf.baseUrl()).isEqualTo("/_loyalty");
        assertThat(mf.route()).isEqualTo("/loyalty/members");
        assertThat(mf.resolvedByServer()).as("the renderer asks for it, with the reader's token").isFalse();
        // a block: Mateu draws it embedded (no breadcrumb, no window title); titled, only the remote's actions stay
        assertThat(mf.chrome()).isEqualTo(EmbeddedChrome.COMPACT);
        assertThat(mf.cssClasses()).isEqualTo("ec-remote-ui ec-remote-block ec-remote-notitle");
        var untitled = new Block("b2", BlockType.REMOTE, null, null, null, null, null, null, null, null,
                RemoteUi.of("riu-class", "/loyalty/members"));
        assertThat(((MicroFrontend) renderer.content(reader("CANARIAS"), untitled)).cssClasses())
                .as("no title of its own: the remote's stays").isEqualTo("ec-remote-ui ec-remote-block");
        assertThat(((MicroFrontend) renderer.content(reader("CANARIAS"), untitled)).chrome()).isEqualTo(EmbeddedChrome.PAGE);
    }

    @Test
    void a_web_component_and_an_iframe_go_through_ec_ui_source() {
        var c = (Element) renderer.content(reader("ANY", "inbox:read"), block("push", "/x", Map.of("mode", "a\"<b>")));
        assertThat(c.name()).isEqualTo("ec-ui-source");
        assertThat(c.cssClasses()).isEqualTo("ec-remote-ui ec-remote-block ec-remote-notitle");
        assertThat(c.attributes()).containsEntry("kind", "web-component").containsEntry("src", "/_inbox/push/push.js")
                .containsEntry("tag", "ec-push-toggle").containsEntry("import", "/_intranet/embed.js")
                .containsEntry("attrs", "{\"mode\":\"a\\\"\\u003cb\\u003e\",\"route\":\"/x\"}");
        var frame = (Element) renderer.content(reader("ANY"), block("wiki", "/pages/1", Map.of()));
        assertThat(frame.attributes()).containsEntry("kind", "iframe").containsEntry("src", "https://wiki.example.com/app/pages/1");
    }

    @Test
    void a_reader_who_may_not_use_the_source_gets_nothing() {
        assertThat(renderer.content(reader("BALEARES"), block("riu-class", "/loyalty/members", Map.of())))
                .as("not its audience").isNull();
        assertThat(renderer.content(reader("ANY"), block("push", null, Map.of()))).as("no scope").isNull();
        assertThat(renderer.content(reader("ANY"), block("off", null, Map.of()))).as("inactive").isNull();
        assertThat(renderer.content(reader("ANY"), block("gone", null, Map.of()))).as("not registered").isNull();
        var stricter = new PageRenderer(mock(Visibility.class), mock(ItemRepository.class), mock(ContentRenderer.class),
                mock(ReaderActions.class), new RemoteUis(sources, named, ""), remote);
        assertThat(stricter.content(reader("ANY"), block("wiki", null, Map.of()))).as("origin no longer allowed").isNull();
    }

    @Test
    void the_visual_editor_gets_a_card_not_the_live_ui() {
        var html = renderer.draftHtml(reader("BALEARES"), block("riu-class", "/loyalty/members", Map.of()));
        assertThat(html).contains("Riu Class").contains("/_loyalty/loyalty/members").contains("Socios")
                .doesNotContain("<script").doesNotContain("<iframe");
        assertThat(renderer.draftHtml(reader("X"), block("push", null, Map.of())))
                .contains("Avisos &lt;del navegador&gt;").contains("&lt;ec-push-toggle&gt;");
        assertThat(renderer.draftHtml(reader("X"), block("off", null, Map.of()))).contains("inactiva");
        assertThat(renderer.draftHtml(reader("X"), block("gone", null, Map.of()))).contains("ya no está registrada");
    }

    @Test
    void a_remote_page_is_the_live_screen_at_its_own_url_or_an_empty_state() {
        var page = new ItemPage(mock(PortalSupport.class), renderer, mock(ReaderActions.class),
                mock(io.mateu.ecdemo1.contenthub.application.usecases.Collaboration.class),
                mock(io.mateu.ecdemo1.contenthub.application.usecases.UsageTracker.class),
                mock(io.mateu.ecdemo1.contenthub.application.usecases.ItemNotifications.class),
                mock(io.mateu.ecdemo1.contenthub.application.drive.DriveDocuments.class), remoteUis, remote);
        page.id = "p1";
        var item = new io.mateu.ecdemo1.contenthub.application.out.VisibleItem("p1", 1, "REMOTE_PAGE", null, "es",
                "Riu Class", "Socios", "", "ui:riu-class/loyalty/members", "eva", null, null, false, false, null, 0);
        var shown = (io.mateu.uidl.fluent.Form) page.remotePage(reader("CANARIAS"), item, new java.util.ArrayList<>());
        assertThat(shown.title()).isEqualTo("Riu Class");
        assertThat(shown.content()).singleElement().isInstanceOfSatisfying(MicroFrontend.class, mf -> {
            assertThat(mf.route()).isEqualTo("/loyalty/members");
            assertThat(mf.cssClasses()).as("a page: its title may name the window; the breadcrumb is the intranet's")
                    .isEqualTo("ec-remote-ui ec-remote-page");
        });
        var refused = (io.mateu.uidl.fluent.Form) page.remotePage(reader("BALEARES"), item, new java.util.ArrayList<>());
        assertThat(refused.content()).singleElement().isInstanceOf(io.mateu.uidl.data.EmptyState.class);
    }

    @Test
    void in_an_email_it_is_a_link_to_the_web() {
        var b = block("riu-class", "/loyalty/members", Map.of());
        assertThat(remote.fallbackHtml(b, "https://intranet.example.com/intranet/noticias/p1?a=1&b=2"))
                .isEqualTo("<p style=\"margin:1rem 0\"><a href=\"https://intranet.example.com/intranet/noticias/p1?a=1&amp;b=2\">"
                        + "Socios — ver en la web</a></p>");
        var untitled = new Block("b2", BlockType.REMOTE, null, null, null, null, null, null, null, null,
                RemoteUi.of("push", null));
        assertThat(remote.fallbackHtml(untitled, "https://x/p")).contains("Avisos &lt;del navegador&gt; — ver en la web");
        assertThat(remote.fallbackText(b, "https://x/p")).isEqualTo("Socios — ver en la web: https://x/p");
    }
}
