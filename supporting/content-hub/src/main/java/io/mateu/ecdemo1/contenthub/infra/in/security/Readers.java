package io.mateu.ecdemo1.contenthub.infra.in.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.uicommons.user.DisplayOnlyTokenClaims;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Who a request is from. The gateway has verified the token before anything gets here, so its claims
 * are read, not verified again: the user is {@code preferred_username} (the users service's UserId).
 * The editorial permissions are the {@code X-User-Scopes} the gateway sets from the users service
 * (it strips any a client sends); without that header — the gateway could not ask — a realm
 * {@code admin} keeps every permission, so the demo user is never locked out. The attributes come from
 * the employee directory, never from the token.
 *
 * <p>"Ver como": an editor picks a profile in the backoffice; until they leave it, the portal reads as
 * that profile, with a banner saying so. Kept here per editor, for an hour.
 */
@Component
@RequiredArgsConstructor
public class Readers {

    public static final String SCOPES_HEADER = "X-User-Scopes";

    final EmployeeDirectory directory;
    final ContentIndex index;
    final Cache<String, String> viewingAs = Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(1)).build();

    public Reader of(HttpRequest request) {
        return of(request == null ? h -> null : request::getHeaderValue, true);
    }

    /** As the portal and the backoffice read: honouring "ver como". */
    public Reader of(Function<String, String> headers) {
        return of(headers, true);
    }

    /** The real reader, whatever profile they are previewing as — for acknowledgements and the MCP. */
    public Reader real(Function<String, String> headers) {
        return of(headers, false);
    }

    Reader of(Function<String, String> headers, boolean honourViewingAs) {
        var claims = DisplayOnlyTokenClaims.fromAuthorizationHeader(headers.apply("Authorization")).orElse(Map.of());
        var userId = Optional.ofNullable((String) claims.get("preferred_username"))
                .orElse((String) claims.getOrDefault("sub", "anonymous"));
        var name = (String) claims.getOrDefault("name", userId);
        var permissions = permissions(headers.apply(SCOPES_HEADER), claims);
        var employee = directory.find(userId);
        var attributes = employee.map(Employee::attributes).orElse(EmployeeAttributes.NONE);
        if (employee.isPresent()) {
            name = employee.get().name();
        }
        String viewing = null;
        if (honourViewingAs && !permissions.isEmpty()) {
            var hash = viewingAs.getIfPresent(userId);
            var profile = hash == null ? null : index.profiles().get(hash);
            if (profile != null) {
                attributes = profile.withLanguage(attributes.language());
                viewing = hash;
            }
        }
        return new Reader(userId, name, attributes, permissions, viewing);
    }

    @SuppressWarnings("unchecked")
    static Set<EditorialPermission> permissions(String scopesHeader, Map<String, Object> claims) {
        var out = EnumSet.noneOf(EditorialPermission.class);
        if (scopesHeader != null) {
            var scopes = Set.of(scopesHeader.trim().split("\\s+"));
            for (var p : EditorialPermission.values()) {
                if (scopes.contains(p.scope())) {
                    out.add(p);
                }
            }
            return out;
        }
        var realm = claims.get("realm_access") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of();
        var roles = realm.get("roles") instanceof Collection<?> c ? c : java.util.List.of();
        if (roles.contains("admin")) {
            out.addAll(Arrays.asList(EditorialPermission.values()));
        }
        return out;
    }

    public void viewAs(String userId, String hash) {
        if (hash == null) {
            viewingAs.invalidate(userId);
        } else {
            viewingAs.put(userId, hash);
        }
    }
}
