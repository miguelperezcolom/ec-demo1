package io.mateu.ecdemo1.contenthub.infra.in.security;

import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.security.CallerIdentity;
import io.mateu.uidl.security.PrincipalResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The caller as Mateu's {@code @EyesOnly} sees it ({@link Editors}, {@link ContentAdmins}): the editorial
 * permissions {@link Readers} reads from the gateway's {@code X-User-Scopes}, as permissions. Mateu does
 * not authenticate and this pod does not either — the gateway verified the token. {@code content:admin}
 * implies the other two, as {@link io.mateu.ecdemo1.contenthub.application.security.Reader#may} does.
 */
@Component
@RequiredArgsConstructor
public class EditorialIdentity implements PrincipalResolver {

    final Readers readers;

    @Override
    public Optional<CallerIdentity> resolve(HttpRequest request) {
        var reader = readers.real(request::getHeaderValue);
        List<String> permissions = reader.permissions().contains(EditorialPermission.ADMIN)
                ? Arrays.stream(EditorialPermission.values()).map(EditorialPermission::scope).toList()
                : reader.permissions().stream().map(EditorialPermission::scope).toList();
        return Optional.of(new CallerIdentity(reader.userId(), List.of(), List.of(), List.of(), permissions));
    }
}
