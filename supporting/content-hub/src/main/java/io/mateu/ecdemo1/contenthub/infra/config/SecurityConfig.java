package io.mateu.ecdemo1.contenthub.infra.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The screens ({@code /_intranet/**}, {@code /mateu/**}) are only reachable through the gateway,
 * which verified the token; who may see the editorial ones is {@code @EyesOnly}'s call
 * (EditorialIdentity). The media URLs carry their own signature. The MCP server ({@code /sse},
 * {@code /mcp/**}) is reached by ia-agent inside the cluster, not through the gateway — so here the token it forwards is verified against Keycloak, and a call without one refused.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                // The SSE stream is written from async dispatches of the request that opened it.
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/sse", "/mcp/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
