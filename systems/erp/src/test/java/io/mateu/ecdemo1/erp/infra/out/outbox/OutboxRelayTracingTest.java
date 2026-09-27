package io.mateu.ecdemo1.erp.infra.out.outbox;

import io.mateu.ecdemo1.erp.tracing.Traces;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.stream.function.StreamOperations;
import java.lang.reflect.Proxy;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The relay puts the trace a message was written in back on its record, as W3C byte[] headers — and
 * a message written with nothing traced goes out exactly as before.
 */
class OutboxRelayTracingTest {

    static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    static final String STORED = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

    final Traces traces = traces();
    List<OutboxMessageEntity> pending = List.of();
    final Message<?>[] sent = new Message[1];
    final OutboxMessageRepository repository = (OutboxMessageRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{OutboxMessageRepository.class},
            (proxy, method, args) -> method.getName().equals("lockPending") ? pending : null);
    final StreamOperations streamBridge = (StreamOperations) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{StreamOperations.class}, (proxy, method, args) -> {
                sent[0] = (Message<?>) args[1];
                return true;
            });
    final OutboxRelay relay = new OutboxRelay(repository, new OutboxProperties(null, 0, null), streamBridge,
            Clock.systemUTC(), traces);

    static Traces traces() {
        var sdk = SdkTracerProvider.builder().build().get("test");
        var tracer = new OtelTracer(sdk, new OtelCurrentTraceContext(), event -> { });
        var propagator = new OtelPropagator(ContextPropagators.create(W3CTraceContextPropagator.getInstance()), sdk);
        var beans = new StaticListableBeanFactory(Map.of("tracer", tracer, "propagator", propagator));
        return new Traces(beans.getBeanProvider(Tracer.class), beans.getBeanProvider(Propagator.class));
    }

    static OutboxMessageEntity message(String traceparent) {
        var message = new OutboxMessageEntity();
        message.seq = 1L;
        message.binding = "partnerEvents";
        message.messageKey = "P1";
        message.eventType = "PartnerChanged";
        message.payload = "{}";
        message.traceparent = traceparent;
        return message;
    }

    @SuppressWarnings("unchecked")
    Message<byte[]> relayed(OutboxMessageEntity message) {
        pending = List.of(message);
        relay.publishPending();
        assertThat(message.publishedAt).isNotNull();
        return (Message<byte[]>) sent[0];
    }

    @Test
    void theStoredTraceGoesOnTheRecordAsAByteHeaderOfTheSameTrace() {
        var record = relayed(message(STORED));

        assertThat(record.getHeaders().get("traceparent")).isInstanceOf(byte[].class);
        var traceparent = new String((byte[]) record.getHeaders().get("traceparent"), StandardCharsets.UTF_8);
        // The same trace, under the relay's own span.
        assertThat(traceparent).startsWith("00-" + TRACE_ID + "-").isNotEqualTo(STORED);
    }

    @Test
    void aMessageWrittenUntracedGoesOutWithoutTraceHeaders() {
        var record = relayed(message(null));

        assertThat(record.getHeaders()).doesNotContainKey("traceparent");
    }

    @Test
    void nothingIsCapturedWhenNoSpanIsCurrent() {
        assertThat(traces.current()).isEmpty();
    }
}
