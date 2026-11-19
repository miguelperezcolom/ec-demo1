package io.mateu.ecdemo1.gateway.infra.config;

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
 * Riu Class's screens (/_loyalty) reach the loyalty service on the data plane's hosts and on the intranet's:
 * the intranet embeds them («Herramientas», «Riu Class · socios»). Without the intranet hosts the embedded
 * screen answered 404 and the portal showed «Esto ya no está disponible».
 */
@SpringBootTest(classes = {GatewayApplication.class, LoyaltyOnIntranetHostsTest.Tokens.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.web-application-type=reactive",
                "spring.cloud.gateway.httpclient.connect-timeout=1000",
                "spring.cloud.gateway.httpclient.response-timeout=2s"})
class LoyaltyOnIntranetHostsTest {
    private static final AtomicInteger forwarded = new AtomicInteger();
    private static final DisposableServer loyalty = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> request.receive().aggregate().asString().defaultIfEmpty("")
                    .flatMap(body -> {
                        forwarded.incrementAndGet();
                        return response.header("Content-Type", "application/json")
                                .sendString(Mono.just("{\"path\":\"" + request.uri() + "\"}")).then();
                    })).bindNow();

    @Autowired private WebTestClient client;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry properties) {
        properties.add("LOYALTY_URL", () -> "http://127.0.0.1:" + loyalty.port());
        // A path without a route of its own must fail rather than pass through a shell.
        properties.add("SHELL_URL", () -> "http://127.0.0.1:1");
        properties.add("SHELL_REDWOOD_URL", () -> "http://127.0.0.1:1");
        properties.add("INTRANET_SHELL_URL", () -> "http://127.0.0.1:1");
        properties.add("INTRANET_SHELL_REDWOOD_URL", () -> "http://127.0.0.1:1");
    }

    @AfterAll static void closeUpstream() { loyalty.disposeNow(); }

    @Test void riuClassScreensReachLoyaltyOnTheDataPlaneAndTheIntranet() {
        for (var host : List.of("ec1.mateu.io", "rw.ec1.mateu.io", "intranet.ec1.mateu.io", "rw-intranet.ec1.mateu.io")) {
            client.post().uri("/_loyalty/mateu/v3/sync/loyalty/accruals").header("Host", host)
                    .headers(h -> h.setBearerAuth("demo")).contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                    .exchange().expectStatus().isOk().expectBody()
                    .jsonPath("$.path").isEqualTo("/_loyalty/mateu/v3/sync/loyalty/accruals");
        }
    }

    @Test void anonymousCallsNeverReachLoyalty() {
        int before = forwarded.get();
        client.post().uri("/_loyalty/mateu/v3/sync/loyalty/accruals").header("Host", "intranet.ec1.mateu.io")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isUnauthorized();
        assertThat(forwarded.get()).isEqualTo(before);
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
