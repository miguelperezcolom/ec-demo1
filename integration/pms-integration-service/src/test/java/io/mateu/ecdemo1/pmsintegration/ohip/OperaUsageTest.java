package io.mateu.ecdemo1.pmsintegration.ohip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every call to Opera counted — by OHIP module, by endpoint, by how it went — and what OHIP says of its rate. */
class OperaUsageTest {

    HttpServer ohip;
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final OperaUsage usage = new OperaUsage(Clock.systemUTC(), registry);
    OhipClient client;

    @BeforeEach
    void start() throws Exception {
        ohip = HttpServer.create(new InetSocketAddress(0), 0);
        ohip.createContext("/oauth/v1/tokens", exchange -> reply(exchange, 200,
                "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":3600}", Map.of()));
        ohip.createContext("/rsv/", exchange -> reply(exchange, 200, "{}",
                Map.of("X-RateLimit-Limit", "100", "X-RateLimit-Remaining", "97")));
        ohip.createContext("/crm/", exchange -> reply(exchange, 429, "{\"title\":\"Too Many Requests\"}",
                Map.of("Retry-After", "30")));
        ohip.start();
        var gateway = "http://localhost:" + ohip.getAddress().getPort();
        var connection = new OhipConnection("XMAR", gateway, "app", "id", "secret", "RIUE");
        client = new OhipClient(new Connections() {
            @Override
            public Optional<OhipConnection> of(String pmsHotelCode) {
                return Optional.of(connection);
            }

            @Override
            public List<IntegrationView> integrations() {
                return List.of();
            }
        }, new OhipProperties(null, null, null, Duration.ofSeconds(2), null, null, null, null, null),
                new TolerantReader(new ObjectMapper()), Clock.systemUTC(), RestClient.builder(), usage);
    }

    @AfterEach
    void stop() {
        ohip.stop(0);
    }

    static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body, Map<String, String> headers)
            throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void everyCallIsCountedByModuleAndEndpointAndA429IsOhipSayingNotSoFast() {
        client.get("XMAR", "/rsv/v1/hotels/{h}/reservations/{id}", "XMAR", "39481700");
        client.get("XMAR", "/rsv/v1/hotels/{h}/reservations/{id}", "XMAR", "39481745");
        assertThatThrownBy(() -> client.get("XMAR", "/crm/v1/profiles/{id}", "20537508"))
                .isInstanceOf(PmsTransientException.class);

        var u = usage.usage();
        assertThat(u.api()).isEqualTo("opera");
        assertThat(u.byPurpose()).containsExactly(Map.entry("reservations", 2L), Map.entry("profiles", 1L),
                Map.entry("token", 1L));
        assertThat(u.byEndpoint()).containsEntry("GET /rsv/v1/hotels/{hotelId}/reservations/{id}", 2L)
                .containsEntry("POST /oauth/v1/tokens", 1L).containsEntry("GET /crm/v1/profiles/{id}", 1L);
        assertThat(u.ours24h()).isEqualTo(4);
        assertThat(u.limited24h()).isEqualTo(1);
        assertThat(u.errors24h()).isZero();
        // What OHIP said of its rate, last: the 429's Retry-After.
        assertThat(u.rateLimit()).containsEntry("retry-after", "30");
        assertThat(registry.get("opera.api.calls").tags("service", "pms-integration", "purpose", "profiles", "outcome", "limited")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("opera.api.calls").tags("purpose", "reservations", "outcome", "ok").counter().count()).isEqualTo(2);
    }

    @Test
    void aRateLimitOhipStatesBecomesGauges() {
        client.get("XMAR", "/rsv/v1/hotels/{h}/reservations", "XMAR");
        assertThat(usage.usage().rateLimit()).containsEntry("x-ratelimit-limit", "100").containsEntry("x-ratelimit-remaining", "97");
        assertThat(registry.get("opera.api.ratelimit.limit").gauge().value()).isEqualTo(100);
        assertThat(registry.get("opera.api.ratelimit.remaining").gauge().value()).isEqualTo(97);
    }

    @Test
    void anOhipThatSaysNothingOfItsRateLeavesTheLimitUnknown() {
        assertThat(usage.usage().orgMax()).isNull();
        assertThat(usage.usage().rateLimit()).isEmpty();
        assertThat(registry.get("opera.api.ratelimit.limit").gauge().value()).isNaN();
    }

    @Test
    void theEndpointIsWhatWasAskedNotOfWhom() {
        assertThat(OperaUsage.endpointOf("/rsv/v1/hotels/XMAR/reservations/39481700"))
                .isEqualTo("/rsv/v1/hotels/{hotelId}/reservations/{id}");
        assertThat(OperaUsage.endpointOf("/rm/config/v1/hotels/XMU/roomTypes")).isEqualTo("/rm/config/v1/hotels/{hotelId}/roomTypes");
        assertThat(OperaUsage.purposeOf("/rm/config/v1/hotels/XMU/roomTypes")).isEqualTo("rooms");
        assertThat(OperaUsage.purposeOf("/xyz/v1/things")).isEqualTo("xyz");
        assertThat(OperaUsage.purposeOf("/")).isEqualTo("other");
    }
}
