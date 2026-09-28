package io.mateu.ecdemo1.pmsintegration.ohip;

import io.mateu.ecdemo1.integration.model.usage.ApiCalls;
import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * How much of Opera's API (OHIP) this service spends: every call it makes, counted by what it was for
 * — the OHIP module it went to (reservations, profiles, the property's configuration, tokens…) — and
 * by endpoint, and how it went; a 429 is OHIP saying "not so fast". It is an interceptor on every
 * RestClient {@link OhipClient} builds, so no call to Opera goes uncounted, whichever method made it.
 *
 * <p>OHIP publishes no daily allowance the way Salesforce does: its gateway throttles by rate. If it
 * says anything about it — the {@code X-RateLimit-*}/{@code RateLimit-*} headers of API gateways, or a
 * {@code Retry-After} on a 429 — the last it said is kept and shown as sent, and the limit and what
 * remains become gauges. The UAT tenant has not been seen to send any; then the limit is unknown.
 *
 * <p>In memory: a restart starts the count again. The Prometheus counter
 * ({@code opera_api_calls_total{service,purpose,outcome}}) is what keeps the history.
 */
@Component
public class OperaUsage implements ClientHttpRequestInterceptor {

    static final String SERVICE = "pms-integration";
    static final Pattern RATE_HEADER = Pattern.compile("(?i)^(x-)?rate-?limit.*|^retry-after$");
    static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");
    /** OHIP's modules, as the first segment of the path says them, in words. */
    static final Map<String, String> MODULES = Map.ofEntries(
            Map.entry("oauth", "token"), Map.entry("rsv", "reservations"), Map.entry("crm", "profiles"),
            Map.entry("rm", "rooms"), Map.entry("rtp", "rates"), Map.entry("ent", "enterprise"),
            Map.entry("csh", "cashiering"), Map.entry("fof", "front-desk"), Map.entry("par", "availability"),
            Map.entry("hsk", "housekeeping"), Map.entry("blk", "blocks"), Map.entry("inv", "inventory"),
            Map.entry("lov", "lists-of-values"));

    final ApiCalls calls;
    final ApiCalls endpoints;
    final Clock clock;
    volatile Map<String, String> rateLimit = Map.of();
    volatile Instant rateLimitSeenAt;
    volatile Double limit = Double.NaN;
    volatile Double remaining = Double.NaN;

    public OperaUsage(Clock clock, MeterRegistry registry) {
        this.clock = clock;
        this.calls = new ApiCalls(clock, (purpose, outcome) -> Counter.builder("opera.api.calls")
                .description("Calls made to Opera (OHIP), by service, purpose and outcome")
                .tag("service", SERVICE).tag("purpose", purpose).tag("outcome", outcome.tag())
                .register(registry).increment());
        this.endpoints = new ApiCalls(clock, null);
        Gauge.builder("opera.api.ratelimit.limit", this, u -> u.limit).tag("service", SERVICE)
                .description("The rate limit OHIP last said, if it says one").register(registry);
        Gauge.builder("opera.api.ratelimit.remaining", this, u -> u.remaining).tag("service", SERVICE)
                .description("What OHIP last said remains of its rate limit, if it says it").register(registry);
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        var path = request.getURI().getPath();
        var purpose = purposeOf(path);
        var endpoint = request.getMethod().name() + " " + endpointOf(path);
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException | RuntimeException e) {
            record(purpose, endpoint, ApiCalls.Outcome.ERROR);
            throw e;
        }
        var status = response.getStatusCode().value();
        record(purpose, endpoint, status == 429 ? ApiCalls.Outcome.LIMITED
                : status >= 400 ? ApiCalls.Outcome.ERROR : ApiCalls.Outcome.OK);
        observe(response.getHeaders().toSingleValueMap());
        return response;
    }

    void record(String purpose, String endpoint, ApiCalls.Outcome outcome) {
        calls.record(purpose, outcome);
        endpoints.record(endpoint, outcome);
    }

    /** Whatever the answer said of a rate limit, kept as said; nothing if it said nothing. */
    void observe(Map<String, String> headers) {
        var said = new LinkedHashMap<String, String>();
        headers.forEach((name, value) -> {
            if (RATE_HEADER.matcher(name).matches()) {
                said.put(name.toLowerCase(), value);
            }
        });
        if (said.isEmpty()) {
            return;
        }
        rateLimit = Map.copyOf(said);
        rateLimitSeenAt = clock.instant();
        limit = number(said, "x-ratelimit-limit", "ratelimit-limit");
        remaining = number(said, "x-ratelimit-remaining", "ratelimit-remaining");
    }

    static Double number(Map<String, String> said, String... names) {
        for (var name : names) {
            var value = said.get(name);
            if (value != null) {
                try {
                    // "100, 100;w=60" (the IETF draft) says the limit first.
                    return Double.parseDouble(value.split("[,;]")[0].trim());
                } catch (NumberFormatException ignored) {
                    // not a number: kept as said, no gauge
                }
            }
        }
        return Double.NaN;
    }

    /** {@code /rsv/v1/hotels/XMAR/reservations/123} → {@code reservations}. */
    static String purposeOf(String path) {
        var segments = segments(path);
        if (segments.isEmpty()) {
            return "other";
        }
        return MODULES.getOrDefault(segments.getFirst(), segments.getFirst());
    }

    /**
     * {@code /rsv/v1/hotels/XMAR/reservations/123} → {@code /rsv/v1/hotels/{hotelId}/reservations/{id}}:
     * what was asked, not of whom — a handful of endpoints, not one per reservation.
     */
    static String endpointOf(String path) {
        var segments = segments(path);
        var out = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            var s = segments.get(i);
            out.append('/');
            if (i > 0 && "hotels".equals(segments.get(i - 1))) {
                out.append("{hotelId}");
            } else if (i > 1 && HAS_DIGIT.matcher(s).matches() && !s.matches("v\\d+")) {
                out.append("{id}");
            } else {
                out.append(s);
            }
        }
        return out.isEmpty() ? "/" : out.toString();
    }

    static List<String> segments(String path) {
        return path == null ? List.of() : java.util.Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toList();
    }

    public ApiUsage usage() {
        return new ApiUsage("opera", SERVICE, null, null, rateLimitSeenAt, calls.total(), calls.byPurpose(),
                endpoints.byPurpose(), null, calls.total(ApiCalls.Outcome.LIMITED), calls.total(ApiCalls.Outcome.ERROR),
                false, null, rateLimit, remaining.isNaN() ? null : remaining.longValue(), calls.lastHour(),
                calls.previousHour(), null);
    }
}
