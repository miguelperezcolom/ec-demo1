package io.mateu.ecdemo1.mapping.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The W3C trace context of what this service does, carried across its outbox so a message relayed
 * later — by another thread, maybe another pod — continues the trace that wrote it: the context is
 * stored with the outbox row and put back on the Kafka record as {@code traceparent}/{@code
 * tracestate} headers, inside a span of the relay's own.
 *
 * <p>Costs nothing when nothing is traced: with no tracer, or no span current, there is no context
 * to store and the relay sends exactly as before.
 */
@Component
public class Traces {

    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
    public static final String BAGGAGE = "baggage";

    final ObjectProvider<Tracer> tracer;
    final ObjectProvider<Propagator> propagator;

    public Traces(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** The W3C headers of the span current on this thread; empty when there is none. */
    public Map<String, String> current() {
        var t = tracer.getIfAvailable();
        var p = propagator.getIfAvailable();
        var span = t == null ? null : t.currentSpan();
        if (span == null || p == null) {
            return Map.of();
        }
        var headers = new LinkedHashMap<String, String>(4);
        p.inject(span.context(), headers, Map::put);
        return headers;
    }

    /** Sets an attribute on the span current on this thread, if there is one. */
    public void tag(String key, String value) {
        var t = tracer.getIfAvailable();
        var span = t == null ? null : t.currentSpan();
        if (span != null && value != null) {
            span.tag(key, value);
        }
    }

    /**
     * Runs {@code send} inside a producer span that continues the stored context, handing it the
     * headers to put on the record. With no stored context, or no tracer, it runs untraced with no
     * headers.
     */
    public void continuing(String traceparent, String tracestate, String name, Consumer<Map<String, String>> send) {
        var t = tracer.getIfAvailable();
        var p = propagator.getIfAvailable();
        if (traceparent == null || t == null || p == null) {
            send.accept(Map.of());
            return;
        }
        var stored = new LinkedHashMap<String, String>(2);
        stored.put(TRACEPARENT, traceparent);
        if (tracestate != null) {
            stored.put(TRACESTATE, tracestate);
        }
        Span span = p.extract(stored, Map::get).name(name).kind(Span.Kind.PRODUCER).start();
        try (var ignored = t.withSpan(span)) {
            var headers = new LinkedHashMap<String, String>(4);
            p.inject(span.context(), headers, Map::put);
            send.accept(headers);
        } catch (RuntimeException | Error e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
