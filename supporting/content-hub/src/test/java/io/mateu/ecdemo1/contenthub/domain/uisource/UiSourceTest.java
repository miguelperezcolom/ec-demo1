package io.mateu.ecdemo1.contenthub.domain.uisource;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The registry's validation: a UI lives behind the gateway (a Mateu UI's prefix, a web component's script)
 * or, an iframe, at an allow-listed https origin — never javascript:, another host or a path that climbs;
 * and who may use it only ever narrows what uses it.
 */
class UiSourceTest {

    static UiSource mateu(String basePath) {
        return new UiSource("riu-class", "Riu Class", UiSourceKind.MATEU_REMOTE, basePath, null, null, Set.of(), true);
    }

    @Test
    void a_mateu_ui_is_a_gateway_prefix() {
        assertThat(mateu("/_loyalty").basePath()).isEqualTo("/_loyalty");
        assertThat(mateu(" /_users ").basePath()).isEqualTo("/_users");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example.com", "javascript:alert(1)", "/_loyalty/members", "/loyalty",
            "//evil.example.com/_x", "/_Loyalty", "/_loyalty?x=1", "", "/_"})
    void a_mateu_ui_is_nothing_else(String basePath) {
        assertThatThrownBy(() -> mateu(basePath)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_web_component_is_a_script_behind_the_gateway_and_a_custom_element_name() {
        var s = new UiSource("push", "Push", UiSourceKind.WEB_COMPONENT, "/_inbox/push/push.js", "ec-push-toggle",
                null, null, true);
        assertThat(s.tagName()).isEqualTo("ec-push-toggle");
        for (var script : List.of("https://cdn.example.com/x.js", "/_inbox/../users/x.js", "/_inbox/push/push.css",
                "javascript:x.js", "/_inbox//x.js")) {
            assertThatThrownBy(() -> new UiSource("push", "Push", UiSourceKind.WEB_COMPONENT, script, "ec-push-toggle",
                    null, null, true)).as(script).isInstanceOf(IllegalArgumentException.class);
        }
        for (var tag : List.of("div", "Ec-Toggle", "ec-ui-source", "ec-visual-editor", "font-face", "x y-z")) {
            assertThatThrownBy(() -> new UiSource("push", "Push", UiSourceKind.WEB_COMPONENT, "/_inbox/push/push.js", tag,
                    null, null, true)).as(tag).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void an_iframe_is_an_https_url_of_an_allow_listed_origin() {
        var s = new UiSource("wiki", "Wiki", UiSourceKind.IFRAME, "https://Wiki.Example.com:8443/app", null, null, null, true);
        assertThat(s.origin()).isEqualTo("https://wiki.example.com:8443");
        s.requireAllowedOrigin(Set.of("https://wiki.example.com:8443"));
        assertThatThrownBy(() -> s.requireAllowedOrigin(Set.of("https://wiki.example.com")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("orígenes permitidos");
        assertThatThrownBy(() -> s.requireAllowedOrigin(Set.of())).isInstanceOf(IllegalArgumentException.class);
        for (var url : List.of("http://wiki.example.com", "javascript:alert(1)", "https://user@wiki.example.com/",
                "https://wiki.example.com/#x", "/_wiki", "data:text/html,<b>x</b>")) {
            assertThatThrownBy(() -> new UiSource("wiki", "Wiki", UiSourceKind.IFRAME, url, null, null, null, true))
                    .as(url).isInstanceOf(IllegalArgumentException.class);
        }
        // the gateway kinds need no list
        mateu("/_loyalty").requireAllowedOrigin(Set.of());
    }

    @Test
    void ids_names_and_scopes_are_checked() {
        assertThatThrownBy(() -> new UiSource("Riu Class", "x", UiSourceKind.MATEU_REMOTE, "/_loyalty", null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UiSource("riu", " ", UiSourceKind.MATEU_REMOTE, "/_loyalty", null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UiSource("riu", "Riu", UiSourceKind.MATEU_REMOTE, "/_loyalty", null, null,
                Set.of("loyalty:read<script>"), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UiSource("riu", "Riu", UiSourceKind.MATEU_REMOTE, "/_loyalty", "x-y", null, null, true))
                .as("only a web component has a tag").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_reader_needs_every_required_scope_and_an_active_source() {
        var s = new UiSource("riu", "Riu", UiSourceKind.MATEU_REMOTE, "/_loyalty", null, null,
                Set.of("loyalty:read", "loyalty:members"), true);
        assertThat(s.usableWith(Set.of("loyalty:read", "loyalty:members", "content:edit"))).isTrue();
        assertThat(s.usableWith(Set.of("loyalty:read"))).isFalse();
        assertThat(s.usableWith(Set.of())).isFalse();
        assertThat(s.usableWith(null)).isFalse();
        var inactive = new UiSource("riu", "Riu", UiSourceKind.MATEU_REMOTE, "/_loyalty", null, null, Set.of(), false);
        assertThat(inactive.usableWith(Set.of("anything"))).isFalse();
    }

    @Test
    void what_uses_a_source_is_seen_only_by_whom_both_admit() {
        var canarias = Audience.in(Attribute.REGION, "CANARIAS");
        var sources = Map.of(UiSource.AUDIENCE_PREFIX + "riu",
                new UiSource("riu", "Riu", UiSourceKind.MATEU_REMOTE, "/_loyalty", null, canarias, Set.of(), true)
                        .visibilityAudience());
        var policy = new VisibilityPolicy(sources::get);
        var reception = Audience.in(Attribute.DEPARTMENT, "RECEPCION");
        var block = new Block("b", BlockType.REMOTE, reception, null, null, null, null, null, null, null,
                RemoteUi.of("riu", "/loyalty/members"));
        var page = new ItemContent(null, Audience.EVERYONE, Map.of(), List.of(block), null, null, false, false, null, null);
        var effective = Audience.effective(page.audience(), block.visibilityAudience());
        assertThat(policy.admits(effective, who("CANARIAS", "RECEPCION"))).isTrue();
        assertThat(policy.admits(effective, who("BALEARES", "RECEPCION"))).as("the source's audience narrows").isFalse();
        assertThat(policy.admits(effective, who("CANARIAS", "COCINA"))).as("the block's own still narrows").isFalse();
        // the editor keeps editing its own narrowing only
        assertThat(Block.isNarrowing(block.audience())).isTrue();

        var remotePage = new ItemContent(null, reception, Map.of(), List.of(), null, null, false, false, null,
                RemoteUi.of("riu", "/loyalty/members").uri());
        assertThat(policy.admits(remotePage.visibilityAudience(ItemType.REMOTE_PAGE), who("BALEARES", "RECEPCION"))).isFalse();
        assertThat(policy.admits(remotePage.visibilityAudience(ItemType.REMOTE_PAGE), who("CANARIAS", "RECEPCION"))).isTrue();
        assertThat(remotePage.visibilityAudience(ItemType.ARTICLE)).isEqualTo(reception);
    }

    @Test
    void an_inactive_or_unknown_source_admits_nobody() {
        var inactive = new UiSource("riu", "Riu", UiSourceKind.MATEU_REMOTE, "/_loyalty", null, null, Set.of(), false);
        var policy = new VisibilityPolicy(Map.of(UiSource.AUDIENCE_PREFIX + "riu", inactive.visibilityAudience())::get);
        assertThat(policy.admits(UiSource.reference("riu"), who("CANARIAS", "RECEPCION"))).isFalse();
        assertThat(policy.admits(UiSource.reference("removed"), who("CANARIAS", "RECEPCION"))).isFalse();
    }

    @Test
    void an_iframe_use_opens_its_route_under_the_url_with_its_attributes_as_query() {
        var s = new UiSource("wiki", "Wiki", UiSourceKind.IFRAME, "https://wiki.example.com/app/", null, null, null, true);
        assertThat(s.frameUrl(RemoteUi.of("wiki", null))).isEqualTo("https://wiki.example.com/app/");
        assertThat(s.frameUrl(new RemoteUi("wiki", "/pages/12", Map.of("lang", "es & en"))))
                .isEqualTo("https://wiki.example.com/app/pages/12?lang=es+%26+en");
    }

    @Test
    void a_mateu_ui_is_chosen_by_route_only() {
        var s = mateu("/_loyalty");
        s.check(RemoteUi.of("riu-class", "/loyalty/members"));
        assertThatThrownBy(() -> s.check(new RemoteUi("riu-class", "/loyalty", Map.of("a", "b"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.check(RemoteUi.of("other", "/x"))).isInstanceOf(IllegalArgumentException.class);
    }

    static EmployeeAttributes who(String region, String department) {
        return new EmployeeAttributes(Map.of(Attribute.REGION, region, Attribute.DEPARTMENT, department), "es");
    }
}
