package io.mateu.ecdemo1.uicommons.user;

import io.mateu.uidl.data.HorizontalLayout;
import io.mateu.uidl.data.Popover;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.interfaces.HttpRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    static String token(String payload) {
        var enc = Base64.getUrlEncoder().withoutPadding();
        return "Bearer " + enc.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    static HttpRequest request(Map<String, String> headers) {
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(), new Class<?>[]{HttpRequest.class},
                (proxy, method, args) -> "getHeaderValue".equals(method.getName()) ? headers.get((String) args[0]) : null);
    }

    static final String ANA = token("{\"name\":\"Ana Pérez\",\"email\":\"ana@example.com\",\"preferred_username\":\"ana\"}");

    @Test
    void claims_are_read_from_the_bearer_token() {
        assertThat(DisplayOnlyTokenClaims.fromAuthorizationHeader(ANA)).get()
                .satisfies(c -> assertThat(c).containsEntry("name", "Ana Pérez"));
        assertThat(DisplayOnlyTokenClaims.fromAuthorizationHeader(null)).isEmpty();
        assertThat(DisplayOnlyTokenClaims.fromAuthorizationHeader("Basic abc")).isEmpty();
        assertThat(DisplayOnlyTokenClaims.fromAuthorizationHeader("Bearer garbage")).isEmpty();
        assertThat(DisplayOnlyTokenClaims.fromAuthorizationHeader("Bearer a.!!!.c")).isEmpty();
    }

    @Test
    void the_console_user_is_the_header_then_the_token_then_console() {
        assertThat(ConsoleUser.of(request(Map.of("X-User-Name", "gw", "Authorization", ANA)))).isEqualTo("gw");
        assertThat(ConsoleUser.of(request(Map.of("Authorization", ANA)))).isEqualTo("Ana Pérez");
        assertThat(ConsoleUser.of(request(Map.of("Authorization", token("{\"email\":\"x@y\"}"))))).isEqualTo("x@y");
        assertThat(ConsoleUser.of(request(Map.of()))).isEqualTo("console");
    }

    @Test
    void the_widget_greets_the_signed_in_user_and_nobody_else() {
        var alone = UserWidget.of(request(Map.of("Authorization", ANA)));
        assertThat(alone).singleElement().isInstanceOf(Popover.class);
        var popover = (Popover) alone.get(0);
        assertThat(((Text) popover.wrapped()).text()).contains("Hola, Ana Pérez");
        // This browser's notifications, between who it is and Logout: drawn by the push script's element.
        assertThat(((io.mateu.uidl.data.VerticalLayout) popover.content()).content())
                .anySatisfy(c -> assertThat(c).isInstanceOfSatisfying(Text.class,
                        t -> assertThat(t.text()).isEqualTo("<ec-push-toggle></ec-push-toggle>")));

        assertThat(UserWidget.withInboxBadge(request(Map.of("Authorization", ANA))))
                .singleElement().isInstanceOf(HorizontalLayout.class);
        assertThat(UserWidget.of(request(Map.of()))).isEmpty();
        assertThat(UserWidget.withInboxBadge(request(Map.of()))).isEmpty();
    }
}
