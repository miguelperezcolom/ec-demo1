package io.mateu.ecdemo1.frontoffice.infra.config;

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
 * cluster, calls it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/mateu/**"))
        .authorizeHttpRequests(auth -> auth.requestMatchers("/mateu/**").authenticated().anyRequest().permitAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
  }
}
