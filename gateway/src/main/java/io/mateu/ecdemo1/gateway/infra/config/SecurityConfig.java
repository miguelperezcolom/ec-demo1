package io.mateu.ecdemo1.gateway.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;

/**
 * What this gateway is for, beyond routing.
 *
 * <p>The orchestrator, the forms engine and the worker only speak HTTP basic auth — none of them
 * understands OIDC — so behind a plain ingress their management UIs would be reachable by anyone
 * who typed the path, and those UIs can pause definitions and cancel processes. Validating the
 * realm's access token here is what closes that, without asking three applications to grow an
 * identity integration. The booking, content and users services have no security of their own at
 * all, and the chat agent spends money per request, so the same applies to them with less margin.
 *
 * <p>Two kinds of caller reach those paths and both carry the token:
 * <ul>
 *   <li>the browser, whose Mateu client puts {@code Authorization: Bearer} on every XHR through a
 *       single axios interceptor, whatever base URL it is calling;
 *   <li>the shell's own pod, which resolves each {@code RemoteMenu} server-side and propagates the
 *       caller's Authorization header when it does.
 * </ul>
 *
 * <p>The one Mateu path that could not carry a token is {@code /mateu/v3/sse/...}: the SSE client
 * is a bare {@code fetch} that sets only Accept and Content-Type, outside the axios interceptor. It
 * is not exempted here because nothing in these UIs uses it — {@code @Action(sse)} defaults to
 * false and none of them sets it. If a future screen does, it will 401 through this gateway, and
 * the fix belongs in Mateu's SSE client rather than in a hole punched here. The chat panel's own
 * stream is a different client and not affected: it sets the Authorization header itself.
 *
 * <p>Public on purpose: the shell itself and everything it serves, because the bootstrap page is
 * what redirects an anonymous visitor to Keycloak — requiring a token to fetch it would mean nobody
 * could ever obtain one; {@code /eventconductor/**}, the workflow-graph web component, which the
 * browser loads as a script tag and script tags send no headers; and the two git webhook receivers,
 * which GitHub calls with no token at all and which the engine authenticates by HMAC over the body.
 * That last one only holds while a webhook secret is configured — a blank secret makes the engine
 * verify nothing, and then these are two open endpoints that re-clone a repository on demand.
 *
 * <h2>The control console</h2>
 *
 * <p>The second host is not another prefix, it is another audience. Everything behind
 * {@code console.ec1.mateu.io} decides what the chat agent is and what it spends — its model, its
 * system prompt, the API key it authenticates with — so a login is not enough there: the
 * {@code ai-admin} realm role is required, and this is the only place that requirement is written.
 * The control shell and the control plane behind it authenticate nothing of their own, exactly
 * like the demo console's backends.
 *
 * <p><strong>{@code ai-admin} and not {@code admin}, and the distinction is the point.</strong>
 * The demo console's {@code admin} drives workflow definitions and processes; it never reaches an
 * LLM credential. This host does, and nothing else. Keeping them as separate realm roles is what
 * lets an operator hold one without the other — the person who rotates the Anthropic key need not
 * be able to cancel a process, and the person who does need not be able to read the key. A single
 * {@code admin} covering both would make every platform admin an AI admin by accident, which is
 * the failure this split exists to prevent.
 *
 * <p>The rule is scoped by host as well as by path because both consoles serve {@code /mateu/**}
 * and both have a catch-all. Scoping it by path alone would either lock the demo console's own
 * shell behind the admin role, or leave this one open — and the second failure is silent.
 *
 * <p>What stays public on the control host is its bootstrap page and its static assets, for the
 * same reason as the demo console's: that page is what sends an anonymous visitor to Keycloak, so
 * requiring a token to fetch it would mean nobody could ever obtain one.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * The control console's host, as the browser asks for it. It has to match what the gateway's
     * routes use, which is why both read the same property.
     */
    private final String controlHost;
    private final KeycloakRealmRoleConverter roleConverter;

    public SecurityConfig(@Value("${CONTROL_HOST:console.ec1.mateu.io}") String controlHost,
                          KeycloakRealmRoleConverter roleConverter) {
        this.controlHost = controlHost;
        this.roleConverter = roleConverter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // The gateway holds no session and serves no forms of its own; every caller
                // authenticates with a bearer token or not at all.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers("/eventconductor/**").permitAll()
                        // Git webhooks, listed explicitly rather than left to the catch-all
                        // below, because "public" is a decision here and not an oversight:
                        // GitHub cannot hold a Keycloak token, so these authenticate by
                        // HMAC-SHA256 over the body, verified by the engine itself.
                        .pathMatchers("/workflow/webhooks/**", "/forms/webhooks/**").permitAll()
                        .pathMatchers("/_workflow/**", "/_forms/**", "/_worker/**").authenticated()
                        // The three demo CRUD services, guarded the same way and for the same
                        // reason: none of them authenticates anything of its own, so this is
                        // the only thing between their screens and whoever types the path.
                        .pathMatchers("/_booking/**", "/_content/**", "/_users/**").authenticated()
                        // The chat agent. Every prompt costs Anthropic tokens against this
                        // deployment's key, so leaving it open is not a UI question, it is a
                        // bill. It can be required because Mateu's chat client does send the
                        // bearer token — it reads it from localStorage and sets the header
                        // itself, unlike the @Action(sse) client described above.
                        .pathMatchers("/ai/**").authenticated()
                        // The control console. Both of these, because the catalogues are rendered
                        // by two pods: /_ia-cp/** is the control plane's own UI, and /mateu/** is
                        // the control shell's endpoint that assembles the page around it. Leaving
                        // either open would leave the console usable.
                        .matchers(onControlHost("/_ia-cp/**", "/mateu/**")).hasRole("admin")
                        .anyExchange().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Without this, a realm admin's token arrives with no authorities and
                        // hasRole denies everything — see KeycloakRealmRoleConverter.
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter)))
                .build();
    }

    /**
     * Matches a path on the control host and nowhere else.
     *
     * <p>The Host header rather than the URI's host: behind ingress-nginx the request line the
     * gateway sees is not the one the browser sent, and the header is what survives the hop. The
     * port is stripped because a browser sends {@code host:443} only sometimes, and a rule that
     * depends on which is a rule that fails in one environment and not the other.
     */
    private ServerWebExchangeMatcher onControlHost(String... patterns) {
        var paths = new OrServerWebExchangeMatcher(Arrays.stream(patterns)
                .map(p -> (ServerWebExchangeMatcher) new PathPatternParserServerWebExchangeMatcher(p))
                .toList());
        return new AndServerWebExchangeMatcher(this::isControlHost, paths);
    }

    private Mono<ServerWebExchangeMatcher.MatchResult> isControlHost(ServerWebExchange exchange) {
        var host = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
        if (host == null) {
            return ServerWebExchangeMatcher.MatchResult.notMatch();
        }
        var withoutPort = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
        return withoutPort.equalsIgnoreCase(controlHost)
                ? ServerWebExchangeMatcher.MatchResult.match()
                : ServerWebExchangeMatcher.MatchResult.notMatch();
    }
}
