package io.mateu.ecdemo1.gateway.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * What this gateway is for, beyond routing.
 *
 * <p>The orchestrator, the forms engine and the worker only speak HTTP basic auth — none of them
 * understands OIDC — so behind a plain ingress their management UIs would be reachable by anyone
 * who typed the path, and those UIs can pause definitions and cancel processes. Validating the
 * realm's access token here is what closes that, without asking three applications to grow an
 * identity integration.
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
 * is not exempted here because nothing in these three UIs uses it — {@code @Action(sse)} defaults
 * to false and none of them sets it. If a future screen does, it will 401 through this gateway, and
 * the fix belongs in Mateu's SSE client rather than in a hole punched here.
 *
 * <p>Public on purpose: the shell itself and everything it serves, because the bootstrap page is
 * what redirects an anonymous visitor to Keycloak — requiring a token to fetch it would mean nobody
 * could ever obtain one; {@code /eventconductor/**}, the workflow-graph web component, which the
 * browser loads as a script tag and script tags send no headers; and the two git webhook receivers,
 * which GitHub calls with no token at all and which the engine authenticates by HMAC over the body.
 * That last one only holds while a webhook secret is configured — a blank secret makes the engine
 * verify nothing, and then these are two open endpoints that re-clone a repository on demand.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

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
                        .anyExchange().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
