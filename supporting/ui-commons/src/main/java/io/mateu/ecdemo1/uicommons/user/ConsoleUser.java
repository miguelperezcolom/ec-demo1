package io.mateu.ecdemo1.uicommons.user;

import io.mateu.uidl.interfaces.HttpRequest;

/**
 * Who is using the console, for the record of what they did: the {@code X-User-Name} header when
 * the gateway set one, otherwise the name in the Keycloak token the shell forwards with each call
 * ({@link DisplayOnlyTokenClaims} — the gateway has already checked the token, so it is only read
 * here). "console" when there is none.
 */
public final class ConsoleUser {

    static final String NOBODY = "console";

    private ConsoleUser() {
    }

    public static String of(HttpRequest httpRequest) {
        try {
            var name = httpRequest.getHeaderValue("X-User-Name");
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (Exception e) {
            return NOBODY;
        }
        return DisplayOnlyTokenClaims.of(httpRequest)
                .flatMap(claims -> {
                    for (var claim : new String[]{"name", "preferred_username", "email"}) {
                        var value = claims.get(claim);
                        if (value instanceof String s && !s.isBlank()) {
                            return java.util.Optional.of(s);
                        }
                    }
                    return java.util.Optional.empty();
                })
                .orElse(NOBODY);
    }
}
