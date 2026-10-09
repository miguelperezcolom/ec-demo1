package io.mateu.ecdemo1.frontoffice.infra.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A resource server, as the consoles' shells: the UI's calls ({@code /mateu/**}) need a Keycloak
 * token; the bootstrap page stays public, since it is what sends a visitor to Keycloak.
 *
 * <p>{@code /api/**} — where the integration writes reservations — is open here and not routed from
 * the internet: the gateway does not take it to this service, and only the connector, inside the
 * cluster, calls it. So is {@code /demo/**} — the demo's seeding (known customers), called by its
 * tooling inside the cluster.
 *
 * <p>The MCP server ({@code /sse}, {@code /mcp/**}) is the reception agent's: ia-agent forwards the
 * token of the person chatting on every call, and a tool knows from it whom the agent acts for — so a
 * call without one is refused. Nor is it routed from the internet: the gateway answers 404 for it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/demo/**", "/mateu/**", "/sse", "/mcp/**"))
        // The SSE stream is written from async dispatches of the request that opened it — already
        // authorized; Spring Security would otherwise judge each dispatch again, with no token.
        .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
            .requestMatchers("/mateu/**", "/sse", "/mcp/**").authenticated()
            .anyRequest().permitAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
  }
}
