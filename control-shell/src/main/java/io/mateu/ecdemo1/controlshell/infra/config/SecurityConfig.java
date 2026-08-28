package io.mateu.ecdemo1.controlshell.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Same shape as the demo shell's: a resource server that guards {@code /mateu/**} and leaves the
 * bootstrap page public, because that page is what redirects an anonymous visitor to Keycloak and
 * requiring a token to fetch it would mean nobody could ever get one.
 *
 * <p>What is <em>not</em> here is the admin check. This shell validates that a caller is signed
 * in; the gateway is what requires the {@code admin} realm role on this host, in one place, ahead
 * of both this pod and the control plane behind it. Putting it in two places would mean two
 * chances to get it wrong and no clear answer to "where is this enforced".
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
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }
}
