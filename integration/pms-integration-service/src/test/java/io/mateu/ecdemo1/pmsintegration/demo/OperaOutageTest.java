package io.mateu.ecdemo1.pmsintegration.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.PmsIntegrationProperties;
import io.mateu.ecdemo1.pmsintegration.config.RetryAlert;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import io.mateu.ecdemo1.pmsintegration.ohip.OhipClient;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaOutage;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsTransientException;
import io.mateu.ecdemo1.pmsintegration.worker.RetryWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.web.client.RestClient;

/** «Simulación: Opera no responde»: every OHIP call fails as a timeout while it is on, and only while. */
class OperaOutageTest {

    /** A clock the test moves. */
    static class MovingClock extends Clock {
        Instant now = Instant.parse("2026-10-03T17:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    HttpServer opera;
    final AtomicInteger reached = new AtomicInteger();
    final MovingClock clock = new MovingClock();
    final OperaOutage outage = new OperaOutage(clock, Duration.ofMillis(50));
    OhipClient client;

    @BeforeEach
    void start() throws Exception {
        opera = HttpServer.create(new InetSocketAddress(0), 0);
        opera.createContext("/", exchange -> {
            reached.incrementAndGet();
            var body = exchange.getRequestURI().getPath().startsWith("/oauth")
                    ? "{\"access_token\":\"t\",\"expires_in\":3600}" : "{}";
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        opera.start();
        var connection = new OhipConnection("XMAR", "http://localhost:" + opera.getAddress().getPort(), "app", "id", "s", "E");
        client = new OhipClient(new Connections() {
            @Override
            public Optional<OhipConnection> of(String hotel) {
                return Optional.of(connection);
            }

            @Override
            public List<IntegrationView> integrations() {
                return List.of();
            }
        }, new OhipProperties(null, null, null, Duration.ofSeconds(2), null, null, null, null, null),
                new TolerantReader(new ObjectMapper()), clock, RestClient.builder(), null, outage);
    }

    @AfterEach
    void stop() {
        opera.stop(0);
    }

    @Test
    void whileOnEveryCallFailsAsATimeoutAndNothingReachesOpera() {
        outage.on(Duration.ofMinutes(15), "ana");

        assertThatThrownBy(() -> client.get("XMAR", "/rsv/v1/hotels/XMAR/reservations"))
                .isInstanceOf(PmsTransientException.class)
                .hasMessageContaining("Simulación: Opera no responde");
        assertThatThrownBy(() -> client.document("XMAR", "/csh/v1/doc.pdf"))
                .isInstanceOf(PmsTransientException.class);
        assertThat(reached).hasValue(0);
        assertThat(outage.status()).isEqualTo(new OperaOutage.Status(true, clock.now, clock.now.plus(Duration.ofMinutes(15)), "ana"));
    }

    @Test
    void offLetsTheCallsThroughAgain() {
        outage.on(Duration.ofMinutes(15), "ana");
        outage.off("ana");

        client.get("XMAR", "/x");

        assertThat(reached).hasValue(2); // the token, the call
        assertThat(outage.status().active()).isFalse();
    }

    @Test
    void itGoesOffByItselfWhenItsTimeIsUp() {
        outage.on(Duration.ofMinutes(15), "ana");
        clock.now = clock.now.plus(Duration.ofMinutes(15));

        client.get("XMAR", "/x");

        assertThat(outage.active()).isFalse();
        assertThat(outage.status()).isEqualTo(new OperaOutage.Status(false, null, null, null));
    }

    @Test
    void theRetryAlertThresholdChangesAtRuntime() {
        var bridge = mock(StreamBridge.class);
        var properties = new PmsIntegrationProperties(null, null, null, null, List.of("NOSHOW"), Duration.ofMinutes(10));
        var alert = new RetryAlert(properties.alertAfter());
        var watch = new RetryWatch(properties, bridge, clock, alert);

        watch.failed("P-1", "upsert-reservation", "MRU01", "MRU01/L1", "Opera does not answer");
        clock.now = clock.now.plus(Duration.ofMinutes(3));
        watch.failed("P-1", "upsert-reservation", "MRU01", "MRU01/L1", "Opera does not answer");
        verify(bridge, never()).send(eq("notifications"), any());

        alert.set(Duration.ofMinutes(2), "ana");
        watch.failed("P-1", "upsert-reservation", "MRU01", "MRU01/L1", "Opera does not answer");
        verify(bridge).send(eq("notifications"), any());

        alert.restore("ana");
        assertThat(alert.after()).isEqualTo(Duration.ofMinutes(10));
    }
}
