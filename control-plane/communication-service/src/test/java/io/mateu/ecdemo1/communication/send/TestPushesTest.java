package io.mateu.ecdemo1.communication.send;

import io.mateu.ecdemo1.communication.store.PushSubscription;
import io.mateu.ecdemo1.communication.store.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestPushesTest {

    PushSubscriptionRepository subscriptions;
    WebPush push;
    TestPushes tests;

    static PushSubscription browser(String id, String username, String app) {
        var s = new PushSubscription();
        s.id = id;
        s.username = username;
        s.app = app;
        return s;
    }

    @BeforeEach
    void setUp() {
        subscriptions = mock(PushSubscriptionRepository.class);
        push = mock(WebPush.class);
        when(push.configured()).thenReturn(true);
        tests = new TestPushes(subscriptions, push, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Test
    void aPersonTestsTheirOwnBrowser() {
        var ana = browser("b1", "ana", null);
        when(subscriptions.findById("b1")).thenReturn(Optional.of(ana));

        var result = tests.toBrowser("ana", "b1");

        assertThat(result.outcome()).isEqualTo(TestPushes.Outcome.SENT);
        var message = ArgumentCaptor.forClass(WebPush.Message.class);
        verify(push).send(eq(ana), message.capture());
        assertThat(message.getValue().title()).isEqualTo("Prueba de avisos");
        assertThat(message.getValue().urgent()).isFalse();
        assertThat(ana.lastSentAt).isEqualTo(Instant.EPOCH);
    }

    @Test
    void notSomebodyElses() {
        when(subscriptions.findById("b1")).thenReturn(Optional.of(browser("b1", "ana", null)));

        assertThat(tests.toBrowser("pepe", "b1").outcome()).isEqualTo(TestPushes.Outcome.NOT_FOUND);
        assertThat(tests.toBrowser("pepe", "nope").outcome()).isEqualTo(TestPushes.Outcome.NOT_FOUND);
        verify(push, never()).send(any(), any(WebPush.Message.class));
    }

    @Test
    void aBrowserThePushServiceNoLongerHasIsDropped() {
        var ana = browser("b1", "ana", null);
        when(subscriptions.findById("b1")).thenReturn(Optional.of(ana));
        doThrow(new WebPush.Gone("410")).when(push).send(eq(ana), any(WebPush.Message.class));

        assertThat(tests.toBrowser("ana", "b1").outcome()).isEqualTo(TestPushes.Outcome.GONE);
        verify(subscriptions).deleteById("b1");
    }

    @Test
    void aRefusalIsSaidNotThrown() {
        var ana = browser("b1", "ana", null);
        when(subscriptions.findById("b1")).thenReturn(Optional.of(ana));
        doThrow(new IllegalStateException("The push service answered 403")).when(push).send(eq(ana), any(WebPush.Message.class));

        var result = tests.toBrowser("ana", "b1");
        assertThat(result.outcome()).isEqualTo(TestPushes.Outcome.FAILED);
        assertThat(result.detail()).contains("403");
    }

    @Test
    void toAPersonIsToEveryBrowserOfTheirs() {
        when(subscriptions.findByUsername("demo")).thenReturn(List.of(browser("c", "demo", null),
                browser("d", "demo", PushSubscription.FRONT_DESK)));

        assertThat(tests.toUser("demo")).extracting(TestPushes.Result::browser).containsExactly("console", "front desk");
        assertThat(tests.toUser("nobody")).singleElement()
                .satisfies(r -> assertThat(r.outcome()).isEqualTo(TestPushes.Outcome.NOT_FOUND));
    }

    @Test
    void withoutVapidKeysNothingIsSent() {
        when(push.configured()).thenReturn(false);

        assertThat(tests.toBrowser("ana", "b1").outcome()).isEqualTo(TestPushes.Outcome.NOT_CONFIGURED);
        assertThat(tests.toUser("ana")).singleElement()
                .satisfies(r -> assertThat(r.outcome()).isEqualTo(TestPushes.Outcome.NOT_CONFIGURED));
    }
}
