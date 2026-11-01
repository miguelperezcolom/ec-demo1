package io.mateu.ecdemo1.gateway.infra.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.gateway.GatewayApplication;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * The filter in the real gateway: Spring Security in front, a real route behind, an upstream that
 * echoes what it received. Proves the principal the filter reads is the verified one and that the
 * stripping happens on the request that actually leaves the gateway.
 */
@SpringBootTest(classes = {GatewayApplication.class, UserAuthorityEnrichmentGatewayTest.Fakes.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.web-application-type=reactive",
                "spring.cloud.gateway.httpclient.connect-timeout=1000",
                "spring.cloud.gateway.httpclient.response-timeout=2s"})
class UserAuthorityEnrichmentGatewayTest {

    private static final ObjectMapper json = new ObjectMapper();
    private static final DisposableServer upstream = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> {
                try {
                    var headers = request.requestHeaders();
                    var payload = json.writeValueAsString(Map.of(
                            "roles", headers.getAll("X-User-Roles"),
                            "scopes", headers.getAll("X-User-Scopes"),
                            "authorization", headers.get("Authorization", "")));
                    return response.header("Content-Type", "application/json").sendString(Mono.just(payload));
                } catch (JsonProcessingException error) {
                    return Mono.error(error);
                }
            }).bindNow();

    @Autowired private WebTestClient client;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry properties) {
        properties.add("ORCHESTRATOR_URL", () -> "http://127.0.0.1:" + upstream.port());
        properties.add("SHELL_URL", () -> "http://127.0.0.1:1");
        properties.add("SHELL_REDWOOD_URL", () -> "http://127.0.0.1:1");
    }

    @AfterAll static void closeUpstream() { upstream.disposeNow(); }

    @Test void anAuthenticatedRequestReachesTheBackendWithTheUsersRolesAndScopesAndItsOwnToken() {
        client.get().uri("/_workflow/x").header("Host", "ec1.mateu.io")
                .header("X-User-Roles", "forged-admin")
                .headers(headers -> headers.setBearerAuth("admin")).exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.roles").isEqualTo(List.of("admin editor"))
                .jsonPath("$.scopes").isEqualTo(List.of("workflows:run"))
                .jsonPath("$.authorization").isEqualTo("Bearer admin");
    }

    @Test void anAnonymousRequestOnAPublicPathLosesWhatItForged() {
        client.get().uri("/eventconductor/workflow-graph.js").header("Host", "ec1.mateu.io")
                .header("X-User-Roles", "admin").header("X-User-Scopes", "everything").exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.roles").isEmpty()
                .jsonPath("$.scopes").isEmpty();
    }

    @Test void whenTheUsersServiceFailsTheRequestStillGoesThroughWithoutTheHeaders() {
        client.get().uri("/_workflow/x").header("Host", "ec1.mateu.io")
                .header("X-User-Scopes", "forged")
                .headers(headers -> headers.setBearerAuth("unknown-to-users")).exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.roles").isEmpty()
                .jsonPath("$.scopes").isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {
        @Bean ReactiveJwtDecoder testTokenDecoder() {
            return token -> Mono.just(Jwt.withTokenValue(token).header("alg", "RS256")
                    .subject("sub-" + token).claim("preferred_username", token)
                    .issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(60))
                    .build());
        }

        @Bean @Primary AuthInfoLookup fakeLookup() {
            return user -> user.equals("admin")
                    ? Mono.just(new AuthInfo("admin editor", "workflows:run"))
                    : Mono.error(new IllegalStateException("users is down"));
        }
    }
}
