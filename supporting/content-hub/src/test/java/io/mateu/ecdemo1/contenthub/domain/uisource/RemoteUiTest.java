package io.mateu.ecdemo1.contenthub.domain.uisource;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A use of a registered UI: a source and a plain route, or attributes that can never be a handler or a script URL. */
class RemoteUiTest {

    @Test
    void a_remote_page_keeps_it_in_its_url_and_reads_it_back() {
        var use = new RemoteUi("riu-class", "/loyalty/members", Map.of("mode", "compact", "q", "a&b=c"));
        assertThat(use.uri()).isEqualTo("ui:riu-class/loyalty/members?mode=compact&q=a%26b%3Dc");
        assertThat(RemoteUi.parse(use.uri())).contains(use);
        assertThat(RemoteUi.parse("ui:riu-class")).contains(RemoteUi.of("riu-class", null));
        assertThat(RemoteUi.parse("https://x")).isEmpty();
        assertThat(RemoteUi.parse(null)).isEmpty();
        assertThatThrownBy(() -> RemoteUi.parse("ui:riu-class/../_users")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"loyalty", "//evil.example.com", "/a/../b", "/a/./b", "/a b", "/a?x=1", "/a#x",
            "javascript:alert(1)", "/a\nb", "/..", "/a/%2e%2e/b"})
    void a_route_is_only_a_path(String route) {
        assertThatThrownBy(() -> RemoteUi.of("riu-class", route)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void routes_that_are_paths_are_fine() {
        assertThat(RemoteUi.of("riu-class", "/").route()).isEqualTo("/");
        assertThat(RemoteUi.of("riu-class", "/loyalty/members/M-1").route()).isEqualTo("/loyalty/members/M-1");
        assertThat(RemoteUi.of("riu-class", " ").route()).isNull();
    }

    @Test
    void attributes_are_never_handlers_styles_or_script_urls() {
        for (var name : List.of("onclick", "onLoad", "style", "class", "id", "is", "slot", "Data", "a b", "x=y")) {
            assertThatThrownBy(() -> new RemoteUi("push", null, Map.of(name, "v"))).as(name)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (var value : List.of("javascript:alert(1)", " JavaScript:alert(1)", "java\tscript:x", "data:text/html,x",
                "vbscript:x", "a\nb", "x".repeat(501))) {
            assertThatThrownBy(() -> new RemoteUi("push", null, Map.of("href", value))).as(value)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new RemoteUi("push", null, Map.of("data-mode", "on", "label", "Avisos")).attributes())
                .containsEntry("data-mode", "on").containsEntry("label", "Avisos");
    }

    @Test
    void the_forms_write_attributes_one_per_line() {
        var attrs = RemoteUi.attributesOf("mode = compact\n\nlabel=Hola = mundo\n");
        assertThat(attrs).containsEntry("mode", "compact").containsEntry("label", "Hola = mundo");
        assertThat(new RemoteUi("push", null, attrs).attributeLines()).isEqualTo("label=Hola = mundo\nmode=compact");
        assertThatThrownBy(() -> RemoteUi.attributesOf("sin igual")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_remote_block_needs_its_use_and_other_blocks_have_none() {
        assertThatThrownBy(() -> new Block("b", BlockType.REMOTE, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        var html = new Block("b", BlockType.HTML, null, null, "<p>x</p>", null, null, null, null, null,
                RemoteUi.of("riu-class", "/x"));
        assertThat(html.remote()).isNull();
        assertThat(html.visibilityAudience()).isEqualTo(Audience.EVERYONE);
        var remote = new Block("b", BlockType.REMOTE, null, null, null, null, null, null, null, null,
                RemoteUi.of("riu-class", "/x"));
        assertThat(remote.visibilityAudience()).isEqualTo(UiSource.reference("riu-class"));
        assertThat(remote.withHtml("<p>y</p>").remote()).isEqualTo(remote.remote());
    }

    @Test
    void a_remote_page_cannot_be_published_without_its_ui() {
        var now = Instant.parse("2026-11-02T10:00:00Z");
        var without = Item.create("p", ItemType.REMOTE_PAGE, "eva", "es", new ItemContent(null, null,
                Map.of("es", new Translation("Riu Class", "", "")), List.of(), null, null, false, false, null, null), now);
        assertThatThrownBy(() -> without.publish("pablo", true, false, now)).isInstanceOf(IllegalStateException.class);
        var with = Item.create("p", ItemType.REMOTE_PAGE, "eva", "es", new ItemContent(null, null,
                Map.of("es", new Translation("Riu Class", "", "")), List.of(), null, null, false, false, null,
                "ui:riu-class/loyalty/members"), now);
        with.publish("pablo", true, false, now);
        assertThat(with.latestVersion()).isPresent();
    }
}
