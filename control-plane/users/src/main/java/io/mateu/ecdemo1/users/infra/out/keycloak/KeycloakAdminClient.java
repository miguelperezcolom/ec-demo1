package io.mateu.ecdemo1.users.infra.out.keycloak;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.users.application.out.identity.IdentityProviderPort;
import io.mateu.ecdemo1.users.application.out.identity.UserIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The identity provider, made concrete: Keycloak's Admin REST API.
 *
 * <p>It authenticates as the realm's admin against the {@code admin-cli} client — the same
 * credential the bootstrap admin logs into the console with — and caches the access token until
 * just before it expires, because minting one is itself two round trips and every prompt would
 * otherwise pay them. A {@code 401} drops the cached token so the next attempt re-mints rather than
 * failing the same way twice.
 *
 * <p><strong>This is broad access, and the comment is the honest record of it.</strong> The
 * bootstrap admin can do anything in the realm; this client needs only {@code manage-users}. The
 * clean version is a confidential client of its own with a service account granted exactly that
 * role, so a leak of this pod's credential cannot reconfigure the realm. It was not done here
 * because it puts a client secret into the realm import — which is in version control — and the
 * bootstrap password is generated at deploy time and kept in a Secret instead. The upgrade is a
 * {@code users-service} client in {@code ec-demo1-realm.json} with {@code serviceAccountsEnabled}
 * and a {@code realm-management/manage-users} mapping, and this method reduced to a client-
 * credentials grant. Until then, the blast radius is a deploy-time Secret, not a committed file.
 *
 * <p>Delivery is an idempotent upsert keyed on username: look the user up, {@code POST} if absent,
 * {@code PUT} if present. That is what lets the outbox relay redeliver safely — a second delivery
 * finds the user and updates it to the same thing. A {@code POST} that races another delivery and
 * loses ({@code 409}) is treated as "already there" and retried as an update, not an error.
 */
@Component
@Slf4j
public class KeycloakAdminClient implements IdentityProviderPort {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final AtomicReference<CachedToken> token = new AtomicReference<>();

    private final String baseUrl;
    private final String realm;
    private final String adminClientId;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean passwordSetupEmail;

    private record CachedToken(String accessToken, Instant expiresAt) {}

    public KeycloakAdminClient(
            @Value("${keycloak.base-url:http://localhost:8080}") String baseUrl,
            @Value("${keycloak.realm:ec-demo1}") String realm,
            @Value("${keycloak.admin.client-id:admin-cli}") String adminClientId,
            @Value("${keycloak.admin.username:admin}") String adminUsername,
            @Value("${keycloak.admin.password:}") String adminPassword,
            @Value("${keycloak.password-setup-email:true}") boolean passwordSetupEmail) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.realm = realm;
        this.adminClientId = adminClientId;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.passwordSetupEmail = passwordSetupEmail;
        log.info("Keycloak Admin API target: {} realm {}", this.baseUrl, this.realm);
    }

    @Override
    public void upsertUser(UserIdentity user) {
        var existingId = findUserId(user.username());
        if (existingId == null) {
            createUser(user);
        } else {
            updateUser(existingId, user);
        }
    }

    @Override
    public void deleteUser(UserIdentity user) {
        var existingId = findUserId(user.username());
        if (existingId == null) {
            // Already gone. Under an at-least-once relay this is the common second delivery, not an
            // anomaly — treat it as done rather than an error to retry forever.
            log.info("Keycloak user {} already absent; nothing to delete", user.username());
            return;
        }
        var uri = URI.create(baseUrl + "/admin/realms/" + realm + "/users/" + existingId);
        var response = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken())
                .DELETE().build(), "delete user");
        if (response.statusCode() != 204 && response.statusCode() != 404) {
            throw fail("delete user " + user.username(), response);
        }
        log.info("Deleted Keycloak user {}", user.username());
    }

    private String findUserId(String username) {
        var uri = URI.create(baseUrl + "/admin/realms/" + realm + "/users?exact=true&username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8));
        var response = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken()).GET().build(), "look up user");
        if (response.statusCode() != 200) {
            throw fail("look up user " + username, response);
        }
        try {
            List<Map<String, Object>> found = mapper.readValue(response.body(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            return found.isEmpty() ? null : String.valueOf(found.get(0).get("id"));
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Keycloak user lookup for " + username, e);
        }
    }

    private void createUser(UserIdentity user) {
        var uri = URI.create(baseUrl + "/admin/realms/" + realm + "/users");
        var response = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken())
                .POST(HttpRequest.BodyPublishers.ofString(representation(user, passwordSetupEmail)))
                .build(), "create user");
        if (response.statusCode() == 201) {
            log.info("Created Keycloak user {}", user.username());
            if (passwordSetupEmail) {
                sendPasswordSetupEmail(user.username());
            }
            return;
        }
        if (response.statusCode() == 409) {
            // Lost a race with another delivery of the same change. It exists now; update it, which
            // is what we would have done had we seen it first. Keeps redelivery idempotent. No
            // password email on this path — the winning delivery already sent one.
            log.info("Keycloak user {} already exists (409); updating instead", user.username());
            var id = findUserId(user.username());
            if (id != null) {
                updateUser(id, user);
                return;
            }
        }
        throw fail("create user " + user.username(), response);
    }

    private void updateUser(String keycloakId, UserIdentity user) {
        var uri = URI.create(baseUrl + "/admin/realms/" + realm + "/users/" + keycloakId);
        // An update never carries a required action: the person may be mid-lifecycle with a
        // password already set, and re-imposing "must set password" on every edit would lock them
        // out on a name change.
        var response = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken())
                .PUT(HttpRequest.BodyPublishers.ofString(representation(user, false))).build(),
                "update user");
        if (response.statusCode() != 204 && response.statusCode() != 200) {
            throw fail("update user " + user.username(), response);
        }
        log.info("Updated Keycloak user {}", user.username());
    }

    /**
     * Ask Keycloak to email the user a link to set their password. Best-effort by design: it runs
     * after the user already exists, so a mail failure — SMTP down, no relay password yet — must not
     * undo a create that otherwise succeeded, or the outbox would retry the whole thing and the user
     * would be created twice (well, once, idempotently, but with a second wasted round). It is
     * logged loudly instead, and an operator can resend from the Keycloak console.
     */
    private void sendPasswordSetupEmail(String username) {
        try {
            var id = findUserId(username);
            if (id == null) {
                return;
            }
            var uri = URI.create(baseUrl + "/admin/realms/" + realm + "/users/" + id
                    + "/execute-actions-email");
            var response = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken())
                    .PUT(HttpRequest.BodyPublishers.ofString("[\"UPDATE_PASSWORD\"]")).build(),
                    "send password-setup email");
            if (response.statusCode() == 204) {
                log.info("Sent set-password email to Keycloak user {}", username);
            } else {
                log.warn("Could not send set-password email to {}: HTTP {} {}. The user exists but "
                        + "has no way to sign in until an operator resends it.",
                        username, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Could not send set-password email to {} ({}). The user exists but has no way "
                    + "to sign in until an operator resends it.", username, e.toString());
        }
    }

    /**
     * The user representation Keycloak accepts on both POST and PUT. Identity only, by design.
     * {@code requirePasswordSetup} adds the one action that makes a brand-new, credential-less user
     * set a password before they can do anything — paired with the email that delivers the link.
     */
    private String representation(UserIdentity user, boolean requirePasswordSetup) {
        try {
            var rep = new java.util.HashMap<String, Object>();
            rep.put("username", user.username());
            rep.put("email", user.email() == null ? "" : user.email());
            rep.put("firstName", user.firstName() == null ? "" : user.firstName());
            rep.put("enabled", user.enabled());
            if (requirePasswordSetup) {
                rep.put("requiredActions", List.of("UPDATE_PASSWORD"));
            }
            return mapper.writeValueAsString(rep);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize user " + user.username(), e);
        }
    }

    private String accessToken() {
        var cached = token.get();
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return cached.accessToken();
        }
        var form = "grant_type=password"
                + "&client_id=" + URLEncoder.encode(adminClientId, StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(adminUsername, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(adminPassword, StandardCharsets.UTF_8);
        var uri = URI.create(baseUrl + "/realms/master/protocol/openid-connect/token");
        var response = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), "obtain admin token");
        if (response.statusCode() != 200) {
            throw fail("obtain admin token", response);
        }
        try {
            var body = mapper.readValue(response.body(), Map.class);
            var accessToken = String.valueOf(body.get("access_token"));
            var expiresIn = ((Number) body.get("expires_in")).longValue();
            // Refresh ten seconds early so a token never expires mid-request.
            token.set(new CachedToken(accessToken,
                    Instant.now().plusSeconds(Math.max(1, expiresIn - 10))));
            return accessToken;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Keycloak token response", e);
        }
    }

    private HttpResponse<String> send(HttpRequest request, String what) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                // The token was rejected — expired early, or revoked. Drop it so the retry re-mints.
                token.set(null);
            }
            return response;
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak call failed (" + what + "): " + e, e);
        }
    }

    private IllegalStateException fail(String what, HttpResponse<String> response) {
        return new IllegalStateException("Keycloak refused to " + what + ": HTTP "
                + response.statusCode() + " " + response.body());
    }
}
