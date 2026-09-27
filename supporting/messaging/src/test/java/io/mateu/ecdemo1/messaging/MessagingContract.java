package io.mateu.ecdemo1.messaging;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What the outbox, the relay and the inbox promise, on whatever database a subclass gives. */
abstract class MessagingContract {

    static final AtomicInteger TABLES = new AtomicInteger();

    abstract DataSource dataSource();

    String outboxTable;
    String inboxTable;

    @BeforeEach
    void freshTables() {
        // Each test its own tables: configurable names are part of what is tested.
        var n = TABLES.incrementAndGet();
        outboxTable = "svc" + n + "_outbox";
        inboxTable = "svc" + n + "_inbox";
    }

    Fixture fixture(int maxAttempts, boolean continueAfterFailure) {
        return fixture(maxAttempts, continueAfterFailure, TraceContexts.NONE);
    }

    Fixture fixture(int maxAttempts, boolean continueAfterFailure, TraceContexts traces) {
        return new Fixture(dataSource(), Fixture.properties(outboxTable, inboxTable, maxAttempts, continueAfterFailure),
                traces);
    }

    // ── the outbox ───────────────────────────────────────────────────────────

    @Test
    void aMessageIsWrittenOnlyIfTheCallersTransactionCommits() {
        var f = fixture(0, false);

        assertThatThrownBy(() -> f.transactions.executeWithoutResult(s -> {
            f.outbox.append("events", "A", "rolled back");
            throw new IllegalStateException("the decision failed");
        })).isInstanceOf(IllegalStateException.class);
        f.append("A", "committed");

        assertThat(f.outbox.messages("events")).extracting(OutboxMessage::payload).containsExactly("committed");
        assertThat(f.outbox.pendingCount()).isEqualTo(1);
    }

    @Test
    void theHeadersGivenGoOnTheRecord() {
        var f = fixture(0, false);
        f.transactions.executeWithoutResult(s ->
                f.outbox.append("events", "A", "Created", "{}", Map.of("x-source", "crs & co=1")));

        f.relay.relayPending();

        var sent = f.transport.sent.getFirst();
        assertThat(sent.message().type()).isEqualTo("Created");
        assertThat(new String(sent.headers().get("x-source"), StandardCharsets.UTF_8)).isEqualTo("crs & co=1");
    }

    // ── the relay ────────────────────────────────────────────────────────────

    @Test
    void theRelaySendsInTheOrderWrittenAndMarksSent() {
        var f = fixture(0, false);
        f.append("A", "a1");
        f.append("B", "b1");
        f.append("A", "a2");
        f.append(null, "n1");

        f.relay.relayPending();

        assertThat(f.transport.payloads()).containsExactly("a1", "b1", "a2", "n1");
        assertThat(f.outbox.pendingCount()).isZero();
        assertThat(f.outbox.messages("events")).allSatisfy(m -> assertThat(m.publishedAt()).isNotNull());

        f.relay.relayPending();
        assertThat(f.transport.sent).hasSize(4);
    }

    @Test
    void aFailedMessageHoldsItsKeyBackWhileOtherKeysGoOn() {
        var f = fixture(0, true);
        f.transport.failing.add("a1");
        f.append("A", "a1");
        f.append("A", "a2");
        f.append("B", "b1");

        var pass = f.relay.relayOnce();
        assertThat(pass).isEqualTo(new OutboxRelay.Pass(1, 1, 1));
        assertThat(f.transport.payloads()).containsExactly("b1");

        // a1 waits out its backoff, and a2 behind it — even though a2 itself is due.
        f.relay.relayPending();
        assertThat(f.transport.payloads()).containsExactly("b1");

        f.clock.advance(Duration.ofSeconds(1));
        f.relay.relayPending();  // a1 fails again: 2 attempts, 2 s now
        assertThat(f.outbox.messages("events").getFirst().attempts()).isEqualTo(2);

        f.transport.failing.clear();
        f.clock.advance(Duration.ofSeconds(1));
        f.relay.relayPending();
        assertThat(f.transport.payloads()).containsExactly("b1");

        f.clock.advance(Duration.ofSeconds(1));
        f.relay.relayPending();
        assertThat(f.transport.payloads()).containsExactly("b1", "a1", "a2");
        assertThat(f.outbox.pendingCount()).isZero();
    }

    @Test
    void byDefaultAFailureEndsThePass() {
        var f = fixture(0, false);
        f.transport.failing.add("a1");
        f.append("A", "a1");
        f.append("B", "b1");

        assertThat(f.relay.relayOnce()).isEqualTo(new OutboxRelay.Pass(0, 1, 0));
        assertThat(f.transport.sent).isEmpty();

        // The next pass: a1 is not due yet, b1 goes.
        f.relay.relayPending();
        assertThat(f.transport.payloads()).containsExactly("b1");
    }

    @Test
    void theBackoffDoublesUpToItsCeiling() {
        var settings = Fixture.properties(outboxTable, inboxTable, 0, false).outbox();

        assertThat(settings.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(settings.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(settings.backoff(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(settings.backoff(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(settings.backoff(40)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void aMessageThatKeepsFailingIsAbandonedAndItsKeyGoesOn() {
        var f = fixture(2, false);
        f.transport.failing.add("a1");
        f.append("A", "a1");
        f.append("A", "a2");

        f.relay.relayPending();
        f.clock.advance(Duration.ofSeconds(1));
        f.relay.relayPending();  // second failure: abandoned
        f.clock.advance(Duration.ofSeconds(1));
        f.relay.relayPending();

        assertThat(f.transport.payloads()).containsExactly("a2");
        var a1 = f.jdbc.queryForMap("select abandoned, attempts, last_error from " + outboxTable + " where payload = 'a1'");
        assertThat(a1.get("abandoned")).isEqualTo(true);
        assertThat(((Number) a1.get("attempts")).intValue()).isEqualTo(2);
        assertThat((String) a1.get("last_error")).contains("refused a1");
    }

    @Test
    void cleanupDeletesWhatWasSentLongerAgoThanTheRetention() {
        var f = fixture(0, false);
        f.append("A", "old");
        f.relay.relayPending();
        f.clock.advance(Duration.ofDays(8));
        f.append("A", "recent");
        f.relay.relayPending();
        f.append("A", "pending");
        f.transport.failing.add("pending");

        assertThat(f.relay.cleanup()).isEqualTo(1);
        assertThat(f.outbox.messages("events")).extracting(OutboxMessage::payload).containsExactly("recent", "pending");
    }

    @Test
    void theTraceAMessageWasWrittenInGoesOnItsRecordAsBytesOfTheSameTrace() {
        var sdk = SdkTracerProvider.builder().build().get("test");
        var tracer = new OtelTracer(sdk, new OtelCurrentTraceContext(), event -> { });
        var propagator = new OtelPropagator(ContextPropagators.create(W3CTraceContextPropagator.getInstance()), sdk);
        var beans = new StaticListableBeanFactory(Map.of("tracer", tracer, "propagator", propagator));
        var traces = new MicrometerTraceContexts(beans.getBeanProvider(Tracer.class), beans.getBeanProvider(Propagator.class));
        var f = fixture(0, false, traces);

        var span = tracer.nextSpan().name("booking.create").start();
        try (var ignored = tracer.withSpan(span)) {
            f.append("A", "traced");
        } finally {
            span.end();
        }
        f.append("B", "untraced");
        f.relay.relayPending();

        var traced = f.transport.sent.get(0);
        assertThat(traced.message().traceparent()).contains(span.context().traceId());
        var header = new String(traced.headers().get("traceparent"), StandardCharsets.UTF_8);
        // The same trace, under the relay's own span.
        assertThat(header).startsWith("00-" + span.context().traceId() + "-").isNotEqualTo(traced.message().traceparent());
        assertThat(f.transport.sent.get(1).headers()).doesNotContainKey("traceparent");
    }

    // ── the inbox ────────────────────────────────────────────────────────────

    @Test
    void theInboxRunsTheWorkOncePerConsumer() {
        var f = fixture(0, false);
        var runs = new AtomicInteger();

        assertThat(f.inbox.once("m1", "router", runs::incrementAndGet)).contains(1);
        assertThat(f.inbox.once("m1", "router", runs::incrementAndGet)).isEmpty();
        assertThat(f.inbox.once("m1", "another consumer", runs::incrementAndGet)).contains(2);
        assertThat(f.inbox.seen("router", "m1")).isTrue();
    }

    @Test
    void workThatFailsLeavesTheIdUnrecordedSoTheRedeliveryRunsIt() {
        var f = fixture(0, false);

        assertThatThrownBy(() -> f.inbox.once("m1", "router", (Runnable) () -> {
            f.outbox.append("events", "A", "consequence");
            throw new IllegalStateException("the work failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(f.inbox.seen("router", "m1")).isFalse();
        assertThat(f.outbox.pendingCount()).isZero();
        assertThat(f.inbox.once("m1", "router", () -> f.outbox.append("events", "A", "consequence"))).isTrue();
        assertThat(f.outbox.pendingCount()).isEqualTo(1);
    }

    @Test
    void theInboxJoinsTheCallersTransaction() {
        var f = fixture(0, false);

        assertThatThrownBy(() -> f.transactions.executeWithoutResult(s -> {
            assertThat(f.inbox.firstTime("router", "m1")).isTrue();
            assertThat(f.inbox.firstTime("router", "m1")).isFalse();
            throw new IllegalStateException("rolled back");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(f.inbox.seen("router", "m1")).isFalse();
    }

    @Test
    void aMessageWithNoIdCannotBeDeduplicatedAndAlwaysRuns() {
        var f = fixture(0, false);
        var runs = new AtomicInteger();

        f.inbox.once(null, "router", (Runnable) runs::incrementAndGet);
        f.inbox.once(" ", "router", (Runnable) runs::incrementAndGet);

        assertThat(runs).hasValue(2);
    }

    // ── the tables the services already have ─────────────────────────────────

    @Test
    void aServicesExistingTablesAreKeptTheirPendingRowsRelayedAndTheirIdsStillSeen() {
        // As the services' own copies had Hibernate create them.
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource());
        jdbc.execute("""
                create table %s (seq bigint generated by default as identity primary key,
                    binding varchar(255) not null, message_key varchar(255), event_type varchar(255) not null,
                    payload varchar(10000) not null, created_at timestamp(6) with time zone not null,
                    published_at timestamp(6) with time zone, traceparent varchar(64), tracestate varchar(512))"""
                .formatted(outboxTable));
        jdbc.execute("""
                create table %s (consumer varchar(255) not null, event_id varchar(255) not null,
                    received_at timestamp(6) with time zone, primary key (consumer, event_id))""".formatted(inboxTable));
        jdbc.update("insert into " + outboxTable + " (binding, message_key, event_type, payload, created_at, published_at) "
                + "values ('events', 'A', 'Old', 'sent before', current_timestamp, current_timestamp)");
        jdbc.update("insert into " + outboxTable + " (binding, message_key, event_type, payload, created_at, traceparent) "
                + "values ('events', 'A', 'Old', 'pending before', current_timestamp, null)");
        jdbc.update("insert into " + inboxTable + " (consumer, event_id, received_at) values ('router', 'm1', current_timestamp)");

        var f = fixture(0, false);
        // An old writer, which knows nothing of the new columns, still inserts.
        jdbc.update("insert into " + outboxTable + " (binding, message_key, event_type, payload, created_at) "
                + "values ('events', 'A', 'Old', 'pending from an old pod', current_timestamp)");
        f.append("A", "new");
        f.relay.relayPending();

        assertThat(f.transport.payloads()).containsExactly("pending before", "pending from an old pod", "new");
        assertThat(f.inbox.once("m1", "router", () -> { })).isFalse();
        // Creating again changes nothing.
        f.schema.create();
        assertThat(f.outbox.messages("events")).hasSize(4);
    }
}
