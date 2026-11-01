package io.mateu.ecdemo1.gateway.infra.enrichment;

import static io.mateu.ecdemo1.gateway.infra.enrichment.UserAuthorityEnrichmentFilter.ROLES_HEADER;
import static io.mateu.ecdemo1.gateway.infra.enrichment.UserAuthorityEnrichmentFilter.SCOPES_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class UserAuthorityEnrichmentFilterTest {

    private final List<String> asked = new ArrayList<>();
    private final AsyncCache<String, AuthInfo> cache =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).buildAsync();

    private UserAuthorityEnrichmentFilter filter(AuthInfoLookup lookup) {
        return new UserAuthorityEnrichmentFilter(user -> {
            asked.add(user);
            return lookup.forUser(user);
        }, cache);
    }

    @Test
    void addsTheUsersRolesAndScopesForAnAuthenticatedCaller() {
        var filter = filter(user -> Mono.just(new AuthInfo("admin editor", "users:read  content:write ")));

        var forwarded = run(filter, authenticated("token-1", "demo"));

        assertThat(asked).containsExactly("demo");
        assertThat(forwarded.getFirst(ROLES_HEADER)).isEqualTo("admin editor");
        assertThat(forwarded.getFirst(SCOPES_HEADER)).isEqualTo("users:read content:write");
        // The Keycloak token goes through as it came: the backends verify it themselves.
        assertThat(forwarded.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-1");
    }

    @Test
    void forgedHeadersAreStrippedFromAnonymousRequests() {
        var filter = filter(user -> Mono.just(new AuthInfo("admin", "all")));

        var forwarded = run(filter, MockServerWebExchange.from(MockServerHttpRequest.get("/_content/x")
                .header(ROLES_HEADER, "admin")
                .header("x-user-scopes", "everything")));

        assertThat(asked).isEmpty();
        assertThat(forwarded.containsKey(ROLES_HEADER)).isFalse();
        assertThat(forwarded.containsKey(SCOPES_HEADER)).isFalse();
    }

    @Test
    void forgedHeadersAreReplacedForAnAuthenticatedCaller() {
        var filter = filter(user -> Mono.just(new AuthInfo("viewer", "content:read")));
        var exchange = authenticated("token-1", "demo", ROLES_HEADER, "admin");

        var forwarded = run(filter, exchange);

        assertThat(forwarded.get(ROLES_HEADER)).containsExactly("viewer");
        assertThat(forwarded.get(SCOPES_HEADER)).containsExactly("content:read");
    }

    @Test
    void anAuthServiceFailureForwardsWithoutTheHeadersAndIsNotCached() {
        var filter = filter(user -> Mono.error(new IllegalStateException("users is down")));

        var forwarded = run(filter, authenticated("token-1", "demo", SCOPES_HEADER, "forged"));

        assertThat(forwarded.containsKey(ROLES_HEADER)).isFalse();
        assertThat(forwarded.containsKey(SCOPES_HEADER)).isFalse();
        assertThat(forwarded.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-1");

        // The failure was not remembered: the next request with the same token asks again.
        run(filter, authenticated("token-1", "demo"));
        assertThat(asked).containsExactly("demo", "demo");
    }

    @Test
    void theSameTokenIsAnsweredFromTheCache() {
        var filter = filter(user -> Mono.just(new AuthInfo("admin", "all")));

        run(filter, authenticated("token-1", "demo"));
        var second = run(filter, authenticated("token-1", "demo"));
        run(filter, authenticated("token-2", "demo"));

        assertThat(second.getFirst(ROLES_HEADER)).isEqualTo("admin");
        assertThat(asked).containsExactly("demo", "demo");
    }

    @Test
    void anUnknownUserIsSentWithEmptyHeaders() {
        var filter = filter(user -> Mono.just(AuthInfo.NONE));

        var forwarded = run(filter, authenticated("token-1", "stranger"));

        assertThat(forwarded.get(ROLES_HEADER)).containsExactly("");
        assertThat(forwarded.get(SCOPES_HEADER)).containsExactly("");
    }

    @Test
    void theUserIsThePreferredUsernameFallingBackToTheSubject() {
        assertThat(UserAuthorityEnrichmentFilter.userOf(jwt("t", "demo", "uuid-1"))).isEqualTo("demo");
        assertThat(UserAuthorityEnrichmentFilter.userOf(jwt("t", null, "uuid-1"))).isEqualTo("uuid-1");
    }

    @Test
    void theAddressAcceptsTheStaticPrefixAndPlainTargets() {
        assertThat(EnrichmentConfig.target("static://users:9191")).isEqualTo("users:9191");
        assertThat(EnrichmentConfig.target("dns:///users:9191")).isEqualTo("dns:///users:9191");
    }

    private static HttpHeaders run(UserAuthorityEnrichmentFilter filter, ServerWebExchange exchange) {
        var seen = new AtomicReference<HttpHeaders>();
        GatewayFilterChain chain = forwarded -> {
            seen.set(forwarded.getRequest().getHeaders());
            return Mono.empty();
        };
        filter.filter(exchange, chain).block(Duration.ofSeconds(5));
        assertThat(seen.get()).as("the request was forwarded").isNotNull();
        return seen.get();
    }

    private static ServerWebExchange authenticated(String token, String username, String... extraHeaders) {
        var request = MockServerHttpRequest.get("/_content/x")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (int i = 0; i < extraHeaders.length; i += 2) {
            request.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        var authentication = new JwtAuthenticationToken(jwt(token, username, "sub-" + username));
        return MockServerWebExchange.from(request).mutate().principal(Mono.just(authentication)).build();
    }

    private static Jwt jwt(String token, String username, String subject) {
        var builder = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (username != null) {
            builder.claim("preferred_username", username);
        }
        return builder.build();
    }
}
