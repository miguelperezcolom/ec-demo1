package io.mateu.ecdemo1.operamock.api;

import io.mateu.ecdemo1.operamock.config.OperaMockProperties;
import io.mateu.ecdemo1.operamock.store.OperaStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/** POST /oauth/v1/tokens: client credentials, with the app key and the enterprise id, as OHIP. */
@RestController
@RequiredArgsConstructor
public class OAuthController {

    final OperaMockProperties properties;
    final OperaStore store;
    final Clock clock;

    @PostMapping("/oauth/v1/tokens")
    public Map<String, Object> token(@RequestHeader(value = "x-app-key", required = false) String appKey,
                                     @RequestHeader(value = "enterpriseId", required = false) String enterpriseId,
                                     @RequestHeader(value = "Authorization", required = false) String authorization,
                                     @RequestParam("grant_type") String grantType) {
        if (!properties.appKey().equals(appKey) || !properties.enterpriseId().equals(enterpriseId)) {
            throw new OperaError(HttpStatus.UNAUTHORIZED, "MOCK-APPKEY", "Invalid application key or enterprise");
        }
        if (!"client_credentials".equals(grantType) || authorization == null || !authorization.startsWith("Basic ")) {
            throw new OperaError(HttpStatus.BAD_REQUEST, "MOCK-GRANT", "client_credentials with basic authentication expected");
        }
        var credentials = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
        if (!credentials.equals(properties.clientId() + ":" + properties.clientSecret())) {
            throw new OperaError(HttpStatus.UNAUTHORIZED, "MOCK-CLIENT", "Invalid client credentials");
        }
        var token = "mock-" + UUID.randomUUID();
        store.issueToken(token, clock.instant().plus(properties.tokenTtl()));
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", properties.tokenTtl().toSeconds());
    }
}
