package io.mateu.ecdemo1.journey.tempo;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.journey.JourneyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Tempo's HTTP API, from inside the cluster: a booking's traces are found by the {@code
 * booking.locator} attribute the CRS and the integration put on their spans (TraceQL), and each is
 * then read whole. Nothing here is reachable from a browser.
 */
@Component
@Slf4j
public class TempoClient {

    static final Duration MAX_WINDOW = Duration.ofHours(167);

    final RestClient http;
    final JourneyProperties properties;
    final Clock clock;

    public TempoClient(JourneyProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().baseUrl(properties.tempoUrl()).requestFactory(factory).build();
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    /** Every trace of one booking in the lookback window, as the search lists them. */
    public List<TraceSummary> tracesOf(String locator) {
        return search("{ span.booking.locator = \"" + quote(locator) + "\" }", 100);
    }

    /**
     * The bookings with a trace lately, newest first — the ones whose locator contains {@code text},
     * when there is one — with each matching span's locator and hotel.
     */
    public List<TraceSummary> recent(String text, int limit) {
        var filter = text == null || text.isBlank()
                ? "span.booking.locator != \"\""
                : "span.booking.locator =~ \".*" + quote(regex(text.trim().toUpperCase())) + ".*\"";
        return search("{ " + filter + " } | select(span.booking.locator, span.hotel.code, span.booking.event)", limit);
    }

    public Optional<JsonNode> trace(String traceId) {
        try {
            return Optional.ofNullable(http.get().uri("/api/traces/{id}", traceId).retrieve().body(JsonNode.class));
        } catch (RuntimeException e) {
            log.info("Tempo has no trace {} (yet?): {}", traceId, e.getMessage());
            return Optional.empty();
        }
    }

    List<TraceSummary> search(String traceQl, int limit) {
        var end = Instant.now(clock).plusSeconds(60);
        // Tempo refuses a search window over 168 h.
        var lookback = properties.lookback().compareTo(MAX_WINDOW) > 0 ? MAX_WINDOW : properties.lookback();
        var start = end.minus(lookback);
        // A URI, not a template: TraceQL's braces would be taken for template variables.
        var uri = UriComponentsBuilder.fromUriString(properties.tempoUrl()).path("/api/search")
                .queryParam("q", "{q}")
                .queryParam("limit", limit)
                .queryParam("spss", 10)
                .queryParam("start", start.getEpochSecond())
                .queryParam("end", end.getEpochSecond())
                .encode().buildAndExpand(java.util.Map.of("q", traceQl)).toUri();
        var answer = http.get().uri(uri).retrieve().body(JsonNode.class);
        return summaries(answer);
    }

    /** Tempo's search answer as summaries, newest first. */
    public static List<TraceSummary> summaries(JsonNode answer) {
        var list = new ArrayList<TraceSummary>();
        if (answer == null) {
            return list;
        }
        for (var trace : answer.path("traces")) {
            var matched = new HashMap<String, String>();
            var sets = new ArrayList<JsonNode>();
            if (trace.has("spanSets")) {
                trace.get("spanSets").forEach(sets::add);
            } else {
                sets.add(trace.path("spanSet"));
            }
            for (var set : sets) {
                for (var span : set.path("spans")) {
                    matched.putAll(TraceParser.attributes(span.path("attributes")));
                }
            }
            var nanos = trace.path("startTimeUnixNano").asLong();
            list.add(new TraceSummary(trace.path("traceID").asText(), Instant.ofEpochSecond(0, nanos),
                    trace.path("durationMs").asLong(), trace.path("rootServiceName").asText(""),
                    trace.path("rootTraceName").asText(""), matched));
        }
        list.sort((a, b) -> b.start().compareTo(a.start()));
        return list;
    }

    static String quote(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String regex(String text) {
        return text.replaceAll("[^A-Za-z0-9-]", "");
    }
}
