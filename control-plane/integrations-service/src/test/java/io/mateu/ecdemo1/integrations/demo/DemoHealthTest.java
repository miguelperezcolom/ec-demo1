package io.mateu.ecdemo1.integrations.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DemoHealthTest {

    HttpServer server;

    @BeforeEach
    void services() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/up", exchange -> reply(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/down", exchange -> reply(exchange, 503, "{\"status\":\"DOWN\"}"));
        server.start();
    }

    static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    DemoProperties properties(String health) {
        return new DemoProperties(null, null, null, "https://ec1", null, health, Duration.ofSeconds(2));
    }

    static OperaOutage.Status status(boolean active, String context) {
        return new OperaOutage.Status(new OperaOutage.Outage(active, Instant.now(), Instant.now().plusSeconds(600), "ana"),
                Duration.ofMinutes(10), Duration.ofMinutes(10), context, context);
    }

    @Test
    void allUpNoOutageAndTheNewContextIsHealthy() {
        var outage = mock(OperaOutage.class);
        when(outage.status()).thenReturn(Optional.of(status(false, "ECDEMO1-10031700")));

        var result = new DemoHealth(properties("booking=" + url("/up") + ",erp=" + url("/up")), outage).check("ECDEMO1-10031700");

        assertThat(result.ok()).isTrue();
        assertThat(result.lines()).containsExactly("OK   booking", "OK   erp", "OK   sin caída de Opera simulada",
                "OK   contexto de Opera ECDEMO1-10031700");
    }

    @Test
    void itSaysWhatIsWrongWithoutFailing() {
        var outage = mock(OperaOutage.class);
        when(outage.status()).thenReturn(Optional.of(status(true, "ECDEMO1-OLD")));

        var result = new DemoHealth(properties("booking=" + url("/down") + ",gone=http://127.0.0.1:1/x"), outage)
                .check("ECDEMO1-10031700");

        assertThat(result.ok()).isFalse();
        assertThat(result.summary()).contains("FAIL booking", "FAIL gone", "FAIL la caída de Opera simulada sigue encendida",
                "FAIL contexto de Opera ECDEMO1-OLD, se esperaba ECDEMO1-10031700");
    }

    @Test
    void theChecksAreReadInOrder() {
        assertThat(properties(" a=http://x , b=http://y,,bad").healthChecks()).containsExactly(
                java.util.Map.entry("a", "http://x"), java.util.Map.entry("b", "http://y"));
    }
}
