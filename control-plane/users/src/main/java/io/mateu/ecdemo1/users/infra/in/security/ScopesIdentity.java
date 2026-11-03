package io.mateu.ecdemo1.users.infra.in.security;

import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.security.CallerIdentity;
import io.mateu.uidl.security.PrincipalResolver;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The caller as Mateu's {@code @EyesOnly} sees it: what the gateway says they hold ({@code X-User-Scopes}), as
 * permissions. This pod doesn't authenticate: the gateway verified the token, and it writes that header itself.
 */
@Component
public class ScopesIdentity implements PrincipalResolver {

    static final String SCOPES_HEADER = "X-User-Scopes";
    static final String USER_HEADER = "X-User-Name";

    @Override
    public Optional<CallerIdentity> resolve(HttpRequest request) {
        var scopes = request.getHeaderValue(SCOPES_HEADER);
        List<String> permissions = scopes == null || scopes.isBlank() ? List.of() : Arrays.asList(scopes.trim().split("\\s+"));
        return Optional.of(new CallerIdentity(request.getHeaderValue(USER_HEADER), List.of(), List.of(), List.of(), permissions));
    }
}
