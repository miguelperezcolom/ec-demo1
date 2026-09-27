package io.mateu.ecdemo1.messaging;

import java.util.Map;

/**
 * The W3C trace context of what a service does, carried across its outbox: {@link #current()} is
 * stored with the row, and {@link #continuing} puts it back on the record the relay sends — later, on
 * another thread, maybe another pod — inside a producer span of the relay's own, so whoever consumes
 * it continues the trace that wrote it.
 *
 * <p>Costs nothing when nothing is traced: with no tracer, or no span current, there is no context to
 * store and the relay sends without trace headers.
 */
public interface TraceContexts {

    String TRACEPARENT = "traceparent";
    String TRACESTATE = "tracestate";
    String BAGGAGE = "baggage";

    /** The W3C headers of the span current on this thread; empty when there is none. */
    Map<String, String> current();

    /** Sets an attribute on the span current on this thread, if there is one. */
    void tag(String key, String value);

    /**
     * Runs {@code send} inside a producer span named {@code name} that continues the stored context,
     * handing it the headers to put on the record. With no stored context, or no tracer, it runs with
     * no headers.
     */
    void continuing(String traceparent, String tracestate, String name, Send send) throws Exception;

    @FunctionalInterface
    interface Send {
        void accept(Map<String, String> headers) throws Exception;
    }

    /** Where nothing is traced. */
    TraceContexts NONE = new TraceContexts() {
        @Override
        public Map<String, String> current() {
            return Map.of();
        }

        @Override
        public void tag(String key, String value) {
        }

        @Override
        public void continuing(String traceparent, String tracestate, String name, Send send) throws Exception {
            send.accept(Map.of());
        }
    };
}
