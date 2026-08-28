package io.mateu.ecdemo1.gateway.infra.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns Keycloak's realm roles into Spring authorities, which is the whole of what
 * {@code hasRole("admin")} needs and the one thing Spring does not do by itself.
 *
 * <p>Out of the box a resource server reads the {@code scope} claim and nothing else. Keycloak
 * does not put realm roles there — they are nested under {@code realm_access.roles} — so without
 * this converter a token belonging to a realm admin arrives with no authorities at all, and
 * {@code hasRole} denies every request while the token is perfectly valid. That failure looks like
 * a broken login rather than a missing mapping, which is why it is worth its own class and this
 * comment.
 *
 * <p>Realm roles only. Keycloak also has per-client roles under {@code resource_access}, and this
 * deployment's realm defines {@code user} and {@code admin} at realm level; reading both would
 * mean two places to grant the same thing.
 */
@Component
public class KeycloakRealmRoleConverter
        implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        return Mono.just(new JwtAuthenticationToken(jwt, authorities(jwt)));
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        var realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        var roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> collection)) {
            return List.of();
        }
        // The ROLE_ prefix is what hasRole() prepends before comparing; without it the check
        // silently never matches.
        return collection.stream()
                .map(String::valueOf)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
    }
}
