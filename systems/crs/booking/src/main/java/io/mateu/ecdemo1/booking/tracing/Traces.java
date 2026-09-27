package io.mateu.ecdemo1.booking.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The spans of the CRS's own use cases and what they are tagged with ({@code booking.locator}, which a
 * booking's trace is searched by). What is carried across the outbox — the context stored with the row
 * and put back on the record — is the shared outbox's ({@link io.mateu.ecdemo1.messaging.TraceContexts}).
 *
 * <p>Costs nothing when nothing is traced: with no tracer, or no span current, it only runs what it is
 * given.
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

    /** No tracer at all: every method does nothing but run what it is given. */
    public static Traces untraced() {
        var none = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        return new Traces(none.getBeanProvider(Tracer.class), none.getBeanProvider(Propagator.class));
    }

    /**
     * Runs {@code work} inside the span current on this thread or, when there is none — a screen's
     * action, a tool call no request observation covers — inside a new root span of this name, so
     * what it does (the outbox rows it writes, and everything downstream of them) has a trace to join.
     */
    public <T> T inSpan(String name, java.util.function.Supplier<T> work) {
        var t = tracer.getIfAvailable();
        if (t == null || t.currentSpan() != null) {
            return work.get();
        }
        var span = t.nextSpan().name(name).start();
        try (var ignored = t.withSpan(span)) {
            return work.get();
        } catch (RuntimeException | Error e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
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
}
