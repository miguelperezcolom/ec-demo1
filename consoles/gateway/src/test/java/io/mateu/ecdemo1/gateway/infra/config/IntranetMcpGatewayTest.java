package io.mateu.ecdemo1.gateway.infra.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.gateway.GatewayApplication;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The intranet's MCP server for desktop AI clients: never anonymous, and each public path reaches the
 * content-hub path that serves it — the SSE stream at /sse, streamable HTTP at /mcp, the SSE messages as is
 * (the path the stream's endpoint event names) — on the data plane's hosts and the intranet's.
 */
@SpringBootTest(classes = {GatewayApplication.class, IntranetMcpGatewayTest.Tokens.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.web-application-type=reactive",
                "spring.cloud.gateway.httpclient.connect-timeout=1000",
                "spring.cloud.gateway.httpclient.response-timeout=2s"})
class IntranetMcpGatewayTest {
    private static final AtomicInteger forwarded = new AtomicInteger();
    private static final ObjectMapper json = new ObjectMapper();
    private static final DisposableServer contentHub = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> request.receive().aggregate().asString().defaultIfEmpty("")
                    .flatMap(body -> {
                        forwarded.incrementAndGet();
                        try {
                            var payload = json.writeValueAsString(Map.of("path", request.uri(),
                                    "method", request.method().name(),
                                    "authorization", request.requestHeaders().get("Authorization", "")));
                            return response.header("Content-Type", "application/json")
                                    .sendString(Mono.just(payload)).then();
                        } catch (JsonProcessingException error) {
                            return Mono.error(error);
                        }
                    })).bindNow();

    @Autowired private WebTestClient client;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry properties) {
        properties.add("CONTENT_HUB_URL", () -> "http://127.0.0.1:" + contentHub.port());
        // A path without a route of its own must fail rather than pass through a shell.
        properties.add("SHELL_URL", () -> "http://127.0.0.1:1");
        properties.add("SHELL_REDWOOD_URL", () -> "http://127.0.0.1:1");
        properties.add("INTRANET_SHELL_URL", () -> "http://127.0.0.1:1");
        properties.add("INTRANET_SHELL_REDWOOD_URL", () -> "http://127.0.0.1:1");
    }

    @AfterAll static void closeUpstream() { contentHub.disposeNow(); }

    @Test void anonymousCallsNeverReachContentHub() {
        int before = forwarded.get();
        client.get().uri("/_intranet/mcp/sse").header("Host", "intranet.ec1.mateu.io").exchange()
                .expectStatus().isUnauthorized();
        client.post().uri("/_intranet/mcp").header("Host", "ec1.mateu.io").exchange()
                .expectStatus().isUnauthorized();
        client.post().uri("/_intranet/mcp/message?sessionId=s1").header("Host", "ec1.mateu.io").exchange()
                .expectStatus().isUnauthorized();
        assertThat(forwarded.get()).isEqualTo(before);
    }

    @Test void eachPublicPathReachesItsContentHubPathWithTheCallersToken() {
        for (var host : List.of("ec1.mateu.io", "rw.ec1.mateu.io", "intranet.ec1.mateu.io", "rw-intranet.ec1.mateu.io")) {
            client.get().uri("/_intranet/mcp/sse").header("Host", host)
                    .headers(h -> h.setBearerAuth("demo")).exchange()
                    .expectStatus().isOk().expectBody().jsonPath("$.path").isEqualTo("/sse")
                    .jsonPath("$.authorization").isEqualTo("Bearer demo");
            client.post().uri("/_intranet/mcp/message?sessionId=s1").header("Host", host)
                    .headers(h -> h.setBearerAuth("demo")).contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                    .exchange().expectStatus().isOk().expectBody()
                    .jsonPath("$.path").isEqualTo("/_intranet/mcp/message?sessionId=s1")
                    .jsonPath("$.method").isEqualTo("POST");
            client.post().uri("/_intranet/mcp").header("Host", host)
                    .headers(h -> h.setBearerAuth("demo")).contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                    .exchange().expectStatus().isOk().expectBody().jsonPath("$.path").isEqualTo("/mcp")
                    .jsonPath("$.authorization").isEqualTo("Bearer demo");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Tokens {
        @Bean ReactiveJwtDecoder testTokenDecoder() {
            return token -> Mono.just(Jwt.withTokenValue(token).header("alg", "RS256")
                    .subject("synthetic-reader").claim("preferred_username", token)
                    .issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(60))
                    .claim("realm_access", Map.of("roles", List.of("user"))).build());
        }
    }
}
