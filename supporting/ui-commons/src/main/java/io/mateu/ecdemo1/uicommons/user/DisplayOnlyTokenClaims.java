package io.mateu.ecdemo1.uicommons.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.uidl.interfaces.HttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * The claims of the Keycloak access token a call carries, read for DISPLAY ONLY: to greet the
 * user, show their email, record a name next to an action.
 *
 * <p>It decodes the token's payload and does NOT verify its signature, and that is deliberate
 * rather than an oversight: by the time a Mateu call reaches the code that reads it, the token has
 * already been verified — by the gateway on the way in, and by the app's own resource server
 * (every one of them guards {@code /mateu/**} with Spring's JWT validation against the realm's
 * JWKS). Verifying again here would be a second network-backed check of the same token, for a
 * greeting.
 *
 * <p>Never use it to decide what someone may do: authorization belongs to what verified the token.
 */
public final class DisplayOnlyTokenClaims {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String BEARER = "Bearer ";

    private DisplayOnlyTokenClaims() {
    }

    /** The claims of the call's bearer token; empty when there is none or it cannot be read. */
    public static Optional<Map<String, Object>> of(HttpRequest httpRequest) {
        return httpRequest == null ? Optional.empty() : fromAuthorizationHeader(httpRequest.getHeaderValue("Authorization"));
    }

    /** The claims of an {@code Authorization: Bearer …} header's token; empty when unreadable. */
    @SuppressWarnings("unchecked")
    public static Optional<Map<String, Object>> fromAuthorizationHeader(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return Optional.empty();
        }
        try {
            var parts = authorization.substring(BEARER.length()).split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }
            return Optional.of(JSON.readValue(
                    new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8), Map.class));
        } catch (Exception e) {
            // an unreadable token names nobody
            return Optional.empty();
        }
    }
}
