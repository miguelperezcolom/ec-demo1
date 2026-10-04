package io.mateu.ecdemo1.shell.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The shell is the only thing in this deployment that authenticates. It is a resource server:
 * the browser gets its token from Keycloak and sends it here, and Spring validates it against
 * the realm's JWKS.
 *
 * <p>Only {@code /mateu/**} — the endpoint the UI actually calls for its content — is guarded.
 * The bootstrap page itself must stay public, because it is what redirects an anonymous visitor
 * to Keycloak in the first place; requiring a token to fetch it would mean nobody could ever
 * get one.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mateu/**").authenticated()
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
            // A request to /mateu/** without a token was refused by the CSRF filter, which runs
            // before authentication: 403, where the answer is "authenticate" — 401. The renderers
            // only refresh the token and retry on a 401, so the user was left in front of "your
            // session is no longer valid" however many times they reloaded. Still refused, CSRF
            // still on: only the status says what is missing. A request that does carry a token
            // and is denied stays a 403.
            .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, denied) -> {
                if (request.getHeader("Authorization") == null) {
                    new BearerTokenAuthenticationEntryPoint().commence(request, response, null);
                } else {
                    new BearerTokenAccessDeniedHandler().handle(request, response, denied);
                }
            }));
        return http.build();
    }
}
