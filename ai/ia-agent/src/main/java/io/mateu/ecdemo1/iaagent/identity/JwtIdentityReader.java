package io.mateu.ecdemo1.iaagent.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;

/**
 * Reads the caller's identity out of the bearer token, without verifying it.
 *
 * <p><strong>Not verifying is the correct choice here, not a shortcut.</strong> The gateway
 * validates the token's signature, issuer and expiry before any request reaches this service — that
 * is the whole reason {@code /ai/**} is an authenticated path. Re-validating here would mean this
 * pod fetching and trusting Keycloak's keys too, a second place to get JWKS and clock skew wrong,
 * to re-establish a fact the gateway already established. So this decodes the claims and trusts
 * them, exactly as far as the gateway's guarantee reaches and no further.
 *
 * <p>Roles come from Keycloak's {@code realm_access.roles}, the same claim the gateway's role
 * converter reads. The tenant claim's name is configurable because it is deployment-specific — a
 * realm that does not mint one simply yields a null tenant, and budgets and routes scoped to a
 * tenant then never match, which is the right behaviour when there is no tenant to speak of.
 */
@Component
public class JwtIdentityReader {

    private static final Logger log = LoggerFactory.getLogger(JwtIdentityReader.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String tenantClaim;

    public JwtIdentityReader(@Value("${ia.identity.tenant-claim:tenant}") String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    public CallerIdentity read(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return CallerIdentity.anonymous();
        }
        try {
            var parts = authorizationHeader.substring(7).trim().split("\\.");
            if (parts.length < 2) {
                return CallerIdentity.anonymous();
            }
            var payload = new String(decodeSegment(parts[1]), StandardCharsets.UTF_8);
            var claims = mapper.readTree(payload);

            var userId = text(claims, "sub");
            var username = claims.hasNonNull("preferred_username")
                    ? claims.get("preferred_username").asText() : userId;
            var roles = new ArrayList<String>();
            var realmRoles = claims.path("realm_access").path("roles");
            if (realmRoles.isArray()) {
                realmRoles.forEach(r -> roles.add(r.asText()));
            }
            var tenant = text(claims, tenantClaim);
            return new CallerIdentity(userId, username, roles, tenant);
        } catch (Exception e) {
            // A malformed token is not this service's problem to diagnose — the gateway would have
            // rejected an invalid one. Answer anonymously and move on.
            log.debug("Could not read identity from bearer token: {}", e.toString());
            return CallerIdentity.anonymous();
        }
    }

    private static byte[] decodeSegment(String segment) {
        // JWT uses base64url without padding; the URL decoder is lenient about the missing padding.
        return Base64.getUrlDecoder().decode(segment);
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
