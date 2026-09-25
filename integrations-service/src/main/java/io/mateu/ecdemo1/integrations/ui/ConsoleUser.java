package io.mateu.ecdemo1.integrations.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.uidl.interfaces.HttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Who is using the console: the name in the Keycloak token the shell forwards with each call. The
 * gateway has already checked the token before letting the call through to the control host's
 * screens, so it is only read here. "console" when there is none.
 */
public final class ConsoleUser {

    static final ObjectMapper JSON = new ObjectMapper();

    private ConsoleUser() {
    }

    public static String of(HttpRequest httpRequest) {
        try {
            var name = httpRequest.getHeaderValue("X-User-Name");
            if (name != null && !name.isBlank()) {
                return name;
            }
            var authorization = httpRequest.getHeaderValue("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                var parts = authorization.substring("Bearer ".length()).split("\\.");
                if (parts.length > 1) {
                    var claims = JSON.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
                    for (var claim : new String[]{"name", "preferred_username", "email"}) {
                        var value = claims.path(claim).asText("");
                        if (!value.isBlank()) {
                            return value;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // an unreadable token names nobody
        }
        return "console";
    }
}
