package io.mateu.ecdemo1.communication.ui.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.uidl.interfaces.HttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * The roles of whoever is looking, from the Keycloak token the shell forwards: realm roles and the
 * roles of every client. The gateway checked the token before letting the call through; here it is
 * only read. No token, no roles — and an inbox that shows only what is everyone's.
 */
public final class Caller {

    static final ObjectMapper JSON = new ObjectMapper();

    private Caller() {
    }

    public static Set<String> roles(HttpRequest httpRequest) {
        return roles(httpRequest.getHeaderValue("Authorization"));
    }

    public static Set<String> roles(String authorization) {
        var roles = new HashSet<String>();
        var claims = claims(authorization);
        if (claims != null) {
            claims.path("realm_access").path("roles").forEach(r -> roles.add(r.asText()));
            claims.path("resource_access").forEach(client -> client.path("roles").forEach(r -> roles.add(r.asText())));
        }
        return roles;
    }

    public static String username(HttpRequest httpRequest) {
        return username(httpRequest.getHeaderValue("Authorization"));
    }

    /** Who it is: the token's preferred username, else its subject. */
    public static String username(String authorization) {
        var claims = claims(authorization);
        if (claims == null) {
            return null;
        }
        var name = claims.path("preferred_username").asText("");
        return name.isBlank() ? claims.path("sub").asText(null) : name;
    }

    static com.fasterxml.jackson.databind.JsonNode claims(String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return null;
            }
            var parts = authorization.substring("Bearer ".length()).split("\\.");
            return JSON.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        } catch (Exception e) {
            // an unreadable token is nobody, with no roles
            return null;
        }
    }
}
