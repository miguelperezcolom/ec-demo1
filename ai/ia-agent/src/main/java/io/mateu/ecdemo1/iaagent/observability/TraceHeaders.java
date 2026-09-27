package io.mateu.ecdemo1.iaagent.observability;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The W3C trace headers ({@code traceparent}, {@code tracestate}) of the span current on this
 * thread, for the calls this service makes with a bare {@link java.net.http.HttpClient}.
 *
 * <p>Needed because none of them is instrumented: the MCP transport, and the three calls to the
 * control plane, are plain JDK clients, so nothing would carry the trace across unless it is put
 * on the request here. With it, an MCP server or the control plane that traces — the engines do —
 * records its side of the call in the same trace as the prompt.
 *
 * <p>Empty, never failing, when tracing is off or nothing is being traced.
 */
@Component
public class TraceHeaders {

    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;

    public TraceHeaders(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** The headers for the current span; captured on the calling thread, sent from any. */
    public Map<String, String> current() {
        var t = tracer.getIfAvailable();
        var p = propagator.getIfAvailable();
        if (t == null || p == null) {
            return Map.of();
        }
        var span = t.currentSpan();
        if (span == null) {
            return Map.of();
        }
        var headers = new LinkedHashMap<String, String>();
        p.inject(span.context(), headers, Map::put);
        return headers;
    }

    public HttpRequest.Builder applyTo(HttpRequest.Builder builder) {
        return applyTo(builder, current());
    }

    public static HttpRequest.Builder applyTo(HttpRequest.Builder builder, Map<String, String> headers) {
        headers.forEach(builder::setHeader);
        return builder;
    }
}
