package io.mateu.ecdemo1.journey.tempo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Reads the OTLP JSON Tempo answers a trace with — {@code batches} (the v1 API) or {@code
 * resourceSpans} (v2, OTLP) — into {@link TraceSpan}s. Ids come base64-encoded and are turned into
 * the hex everybody else prints; a span sent twice (a retried export) is kept once.
 */
public final class TraceParser {

    static final String DEPLOYMENT_PREFIX = "ec-demo1-";

    private TraceParser() {
    }

    public static List<TraceSpan> parse(JsonNode trace) {
        var root = trace.has("trace") ? trace.get("trace") : trace;
        var batches = root.has("batches") ? root.get("batches") : root.path("resourceSpans");
        var spans = new ArrayList<TraceSpan>();
        var seen = new HashSet<String>();
        for (var batch : batches) {
            var service = service(batch.path("resource").path("attributes"));
            var scopes = batch.has("scopeSpans") ? batch.get("scopeSpans") : batch.path("instrumentationLibrarySpans");
            for (var scope : scopes) {
                for (var span : scope.path("spans")) {
                    var id = id(span.path("spanId").asText(null));
                    if (id == null || !seen.add(id)) {
                        continue;
                    }
                    var attributes = attributes(span.path("attributes"));
                    var status = span.path("status");
                    var error = "STATUS_CODE_ERROR".equals(status.path("code").asText()) || "2".equals(status.path("code").asText());
                    var message = status.path("message").asText(null);
                    for (var event : span.path("events")) {
                        var eventAttributes = attributes(event.path("attributes"));
                        if (eventAttributes.containsKey("exception.message")) {
                            error = true;
                            message = eventAttributes.get("exception.message");
                        }
                    }
                    if (error && message == null) {
                        message = attributes.getOrDefault("error", attributes.get("exception"));
                    }
                    spans.add(new TraceSpan(id, id(span.path("parentSpanId").asText(null)), service,
                            span.path("name").asText(""), kind(span.path("kind").asText("")),
                            span.path("startTimeUnixNano").asLong(), span.path("endTimeUnixNano").asLong(),
                            attributes, error, message));
                }
            }
        }
        return spans;
    }

    static String service(JsonNode attributes) {
        var name = attributes(attributes).getOrDefault("service.name", "unknown");
        return name.startsWith(DEPLOYMENT_PREFIX) ? name.substring(DEPLOYMENT_PREFIX.length()) : name;
    }

    static Map<String, String> attributes(JsonNode attributes) {
        var map = new HashMap<String, String>();
        for (var attribute : attributes) {
            var value = attribute.path("value");
            var text = value.has("stringValue") ? value.get("stringValue").asText()
                    : value.has("intValue") ? value.get("intValue").asText()
                    : value.has("boolValue") ? value.get("boolValue").asText()
                    : value.has("doubleValue") ? value.get("doubleValue").asText()
                    : value.toString();
            map.put(attribute.path("key").asText(), text);
        }
        return map;
    }

    /** "SPAN_KIND_SERVER" (or 2) as "SERVER". */
    static String kind(String kind) {
        return switch (kind) {
            case "SPAN_KIND_SERVER", "2" -> "SERVER";
            case "SPAN_KIND_CLIENT", "3" -> "CLIENT";
            case "SPAN_KIND_PRODUCER", "4" -> "PRODUCER";
            case "SPAN_KIND_CONSUMER", "5" -> "CONSUMER";
            default -> "INTERNAL";
        };
    }

    /** An OTLP JSON id — base64 from Tempo, hex from some exporters — as hex. */
    static String id(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.matches("[0-9a-fA-F]{16}|[0-9a-fA-F]{32}")) {
            return raw.toLowerCase();
        }
        try {
            return HexFormat.of().formatHex(Base64.getDecoder().decode(raw));
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }
}
