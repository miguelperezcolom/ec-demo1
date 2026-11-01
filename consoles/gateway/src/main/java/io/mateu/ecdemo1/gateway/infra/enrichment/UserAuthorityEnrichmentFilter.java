package io.mateu.ecdemo1.gateway.infra.enrichment;

import com.github.benmanes.caffeine.cache.AsyncCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Tells the backends what the caller holds in the users service: {@code X-User-Roles} and
 * {@code X-User-Scopes}, each space-separated, from {@code AuthService.GetAuthInfo}.
 *
 * <p>This is the demo's api-gw TokenEnrichmentFilter, ported with one deliberate difference: the
 * original re-signs the access token with the gateway's own key, putting the roles in
 * {@code realm_access.roles} and the scopes in {@code scope}. That cannot be done here, because
 * several backends verify the token themselves against Keycloak's JWKS and issuer — the four
 * shells and the front office (spring-boot-starter-oauth2-resource-server with issuer-uri and
 * jwk-set-uri; every one guards {@code /mateu/**}, see ui-commons' DisplayOnlyTokenClaims) — and
 * the shells forward the caller's Authorization when they resolve a RemoteMenu, so whatever this
 * filter put there would reach them too. The workflow-execution route (/workflow/api/**) is
 * documented as preserving the caller's bearer token, and ia-control-plane verifies workflow
 * tokens against Keycloak's keys as well. A token signed by the gateway, with the
 * gateway as issuer, would be a 401 at every one of them. So {@code Authorization} goes through
 * untouched, and the users service's view travels next to it.
 *
 * <p><strong>The headers are only trustworthy because this filter always strips them
 * first.</strong> Whatever a client sent under these names is removed on every request, token or
 * not, enrichment working or not — otherwise anyone could type {@code X-User-Roles: admin} into a
 * request and a backend reading the header would believe it. A backend may rely on them only for
 * traffic that came through this gateway; a caller inside the cluster can still forge them.
 *
 * <p>The user is read from the token Spring Security already verified (the exchange's principal),
 * never from the raw header: an unverified token does not get this far on a protected path, and
 * on a public one a token that fails verification is rejected by the resource server before any
 * route runs. So the headers are present exactly when the request carried a valid Keycloak token.
 * The user is {@code preferred_username} — in this realm the users service's own UserId, which is
 * what AuthService looks users up by — falling back to {@code sub}.
 *
 * <p>Best-effort, like the original's onErrorResume: if AuthService fails or times out, the
 * request goes on without the two headers and a warning is logged. Authentication was already
 * decided by the gateway; this is extra information, and its absence must never be a 500. Present
 * with an empty value means the users service answered and grants nothing (a Keycloak account it
 * has no record of); absent means nobody knows.
 */
@Component
public class UserAuthorityEnrichmentFilter implements GlobalFilter, Ordered {

    public static final String ROLES_HEADER = "X-User-Roles";
    public static final String SCOPES_HEADER = "X-User-Scopes";

    private static final Logger log = LoggerFactory.getLogger(UserAuthorityEnrichmentFilter.class);

    private final AuthInfoLookup lookup;
    private final AsyncCache<String, AuthInfo> cache;

    public UserAuthorityEnrichmentFilter(AuthInfoLookup lookup, AsyncCache<String, AuthInfo> authInfoCache) {
        this.lookup = lookup;
        this.cache = authInfoCache;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var stripped = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(ROLES_HEADER);
                    headers.remove(SCOPES_HEADER);
                }))
                .build();
        return stripped.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(authentication -> enrich(stripped, authentication.getToken()))
                .defaultIfEmpty(stripped)
                .flatMap(chain::filter);
    }

    private Mono<ServerWebExchange> enrich(ServerWebExchange exchange, Jwt token) {
        var user = userOf(token);
        if (user == null) {
            return Mono.just(exchange);
        }
        return Mono.fromFuture(() -> cache.get(token.getTokenValue(),
                        (key, executor) -> lookup.forUser(user).toFuture()))
                .map(info -> (ServerWebExchange) exchange.mutate()
                        .request(request -> request.headers(headers -> {
                            headers.set(ROLES_HEADER, info.roles());
                            headers.set(SCOPES_HEADER, info.scopes());
                        }))
                        .build())
                .onErrorResume(error -> {
                    // The user and the reason, never the token: it is a credential until it expires.
                    log.warn("Could not enrich the request of {} with its roles and scopes, forwarding without them: {}",
                            user, error.toString());
                    return Mono.just(exchange);
                });
    }

    static String userOf(Jwt token) {
        var username = token.getClaimAsString("preferred_username");
        if (username != null && !username.isBlank()) {
            return username;
        }
        var subject = token.getSubject();
        return subject != null && !subject.isBlank() ? subject : null;
    }

    /**
     * Early, so the stripping happens before any route filter could copy or log the client's
     * values; any order before the routing filter (LOWEST_PRECEDENCE) would forward the result.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
