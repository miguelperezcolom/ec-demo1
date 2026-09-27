package io.mateu.ecdemo1.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@link TraceContexts} on Micrometer Tracing, with whatever tracer and propagator the service has. */
public class MicrometerTraceContexts implements TraceContexts {

    final ObjectProvider<Tracer> tracer;
    final ObjectProvider<Propagator> propagator;

    public MicrometerTraceContexts(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
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

    @Override
    public void tag(String key, String value) {
        var t = tracer.getIfAvailable();
        var span = t == null ? null : t.currentSpan();
        if (span != null && value != null) {
            span.tag(key, value);
        }
    }

    @Override
    public void continuing(String traceparent, String tracestate, String name, Send send) throws Exception {
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
        } catch (Exception | Error e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
