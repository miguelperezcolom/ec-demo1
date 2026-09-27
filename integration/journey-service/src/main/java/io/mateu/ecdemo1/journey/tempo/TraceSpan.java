package io.mateu.ecdemo1.journey.tempo;

import java.util.Map;

/**
 * One span of a trace, as Tempo keeps it: which service did what, when, and with which attributes.
 *
 * @param service      the service's name without the deployment's prefix ("booking", "orchestrator")
 * @param kind         SERVER, CLIENT, PRODUCER, CONSUMER or INTERNAL
 * @param errorMessage what went wrong, when the span says it went wrong
 */
public record TraceSpan(String spanId, String parentId, String service, String name, String kind,
                        long startNanos, long endNanos, Map<String, String> attributes, boolean error,
                        String errorMessage) {

    public String attr(String key) {
        return attributes.get(key);
    }

    public boolean has(String key) {
        var value = attributes.get(key);
        return value != null && !value.isBlank();
    }

    public long durationMillis() {
        return Math.max(0, (endNanos - startNanos) / 1_000_000);
    }

    public boolean isServer() {
        return "SERVER".equals(kind);
    }

    public boolean isClient() {
        return "CLIENT".equals(kind);
    }
}
