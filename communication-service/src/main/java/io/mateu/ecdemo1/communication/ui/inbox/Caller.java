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
        var roles = new HashSet<String>();
        try {
            var authorization = httpRequest.getHeaderValue("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return roles;
            }
            var parts = authorization.substring("Bearer ".length()).split("\\.");
            var claims = JSON.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            claims.path("realm_access").path("roles").forEach(r -> roles.add(r.asText()));
            claims.path("resource_access").forEach(client -> client.path("roles").forEach(r -> roles.add(r.asText())));
        } catch (Exception e) {
            // an unreadable token has no roles
        }
        return roles;
    }
}
