package io.mateu.ecdemo1.communication.rest;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.send.TestPushes;
import io.mateu.ecdemo1.communication.send.WebPushCrypto;
import io.mateu.ecdemo1.communication.store.PushSubscription;
import io.mateu.ecdemo1.communication.store.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PushControllerTest {

    static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";

    static String token(String username, String... roles) {
        var enc = Base64.getUrlEncoder().withoutPadding();
        var claims = "{\"preferred_username\":\"" + username + "\",\"realm_access\":{\"roles\":[\""
                + String.join("\",\"", roles) + "\"]}}";
        return "Bearer " + enc.encodeToString("{}".getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    static PushController.Subscription subscription() {
        var p256dh = new byte[65];
        p256dh[0] = 4;
        return new PushController.Subscription(ENDPOINT, new PushController.Keys(WebPushCrypto.b64(p256dh), "YXV0aA"));
    }

    PushSubscriptionRepository subscriptions;
    TestPushes tests;
    PushController controller;

    @BeforeEach
    void setUp() {
        subscriptions = mock(PushSubscriptionRepository.class);
        tests = mock(TestPushes.class);
        when(subscriptions.findById(any())).thenReturn(Optional.empty());
        var properties = new CommunicationProperties(null, null, 0, null, null,
                new CommunicationProperties.Push("pub", "priv", null, List.of("Front.ec1.mateu.io")));
        controller = new PushController(properties, subscriptions, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), tests);
    }

    PushSubscription saved() {
        var captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptions).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void aBrowserThatSubscribesFromTheFrontOfficeIsTheFrontDesks() {
        var answer = controller.subscribe(token("demo", "user", "ai-admin"), "https://front.ec1.mateu.io", null, subscription());

        assertThat(answer.getStatusCode().value()).isEqualTo(204);
        var s = saved();
        assertThat(s.atFrontDesk()).isTrue();
        assertThat(s.username).isEqualTo("demo");
        assertThat(s.roles.split(",")).containsExactlyInAnyOrder("user", "ai-admin");
        assertThat(s.subscribedAt).isEqualTo(Instant.EPOCH);
    }

    @Test
    void aBrowserThatSubscribesFromAConsoleIsTheConsoles() {
        controller.subscribe(token("demo", "ai-admin"), "https://console.ec1.mateu.io", "front.ec1.mateu.io", subscription());

        assertThat(saved().app).isNull();
    }

    @Test
    void withoutAnOriginTheForwardedHostSays() {
        controller.subscribe(token("demo", "ai-admin"), null, "front.ec1.mateu.io:443", subscription());

        assertThat(saved().atFrontDesk()).isTrue();
    }

    @Test
    void nobodySubscribesWithoutAToken() {
        assertThat(controller.subscribe(null, null, null, subscription()).getStatusCode().value()).isEqualTo(401);
        verify(subscriptions, never()).save(any());
    }

    @Test
    void aPersonTurnsOffOnlyTheirOwnBrowser() {
        var theirs = new PushSubscription();
        theirs.id = PushController.id(ENDPOINT);
        theirs.username = "ana";
        when(subscriptions.findById(theirs.id)).thenReturn(Optional.of(theirs));

        assertThat(controller.unsubscribe(token("pepe", "user"), ENDPOINT).getStatusCode().value()).isEqualTo(204);
        verify(subscriptions, never()).delete(any());

        controller.unsubscribe(token("ana", "user"), ENDPOINT);
        verify(subscriptions).delete(theirs);

        assertThat(controller.unsubscribe(null, ENDPOINT).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void theTestGoesToTheCallersBrowserByItsEndpoint() {
        var sent = new TestPushes.Result(TestPushes.Outcome.SENT, "console", "Sent");
        when(tests.toBrowser(eq("ana"), eq(PushController.id(ENDPOINT)))).thenReturn(sent);

        var answer = controller.test(token("ana", "user"), new PushController.TestRequest(ENDPOINT));

        assertThat(answer.getStatusCode().value()).isEqualTo(200);
        assertThat(answer.getBody()).isEqualTo(sent);
    }

    @Test
    void noTestWithoutATokenOrAnEndpoint() {
        assertThat(controller.test(null, new PushController.TestRequest(ENDPOINT)).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.test(token("ana"), new PushController.TestRequest(" ")).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(tests);
    }

    @Test
    void whatABrowserPostsIsReadAsItComes() throws Exception {
        // PushSubscription.toJSON() as Chrome sends it, expirationTime included, read by a mapper that
        // fails on unknown fields — as this application's does.
        var json = new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        var s = json.readValue("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\",\"expirationTime\":null,"
                + "\"keys\":{\"p256dh\":\"BAAA\",\"auth\":\"YXV0aA\",\"extra\":1}}", PushController.Subscription.class);
        assertThat(s.endpoint()).isEqualTo("https://fcm.googleapis.com/fcm/send/x");
        assertThat(s.keys().auth()).isEqualTo("YXV0aA");
        assertThat(json.readValue("{\"endpoint\":\"e\",\"x\":1}", PushController.TestRequest.class).endpoint()).isEqualTo("e");
    }

    @Test
    void theOriginsHostIsReadWithoutPortOrCase() {
        assertThat(PushController.host("https://Front.ec1.mateu.io:443")).isEqualTo("front.ec1.mateu.io");
        assertThat(PushController.host("null")).isNull();
        assertThat(PushController.host(null)).isNull();
    }
}
