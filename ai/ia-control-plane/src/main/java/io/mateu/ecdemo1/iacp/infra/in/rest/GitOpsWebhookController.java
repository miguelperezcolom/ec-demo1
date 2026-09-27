package io.mateu.ecdemo1.iacp.infra.in.rest;

import io.mateu.ecdemo1.iacp.application.usecases.gitops.ReconcileCatalogueUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The GitHub push webhook: what turns a merge in the config repo into a reconcile here.
 *
 * <p><strong>Public, and verified by HMAC rather than a token.</strong> GitHub cannot carry a
 * Keycloak token, so this endpoint sits outside the {@code ai-admin} rule — reachable, on the
 * control host, at a path the gateway does not guard. What stands in for authentication is the
 * signature GitHub computes over the body with a shared secret: recomputed here and compared in
 * constant time, it proves the call came from whoever holds the secret. It is the same bargain the
 * engine's git webhooks make, and it holds only while the secret is set — a blank secret verifies
 * nothing, which is logged, not hidden.
 *
 * <p>It answers fast and works in the background. GitHub times a webhook out in seconds, while a
 * reconcile fetches a repo and rewrites catalogues, so the handler hands the work to an executor and
 * returns {@code 202} at once. The reconcile itself is single-flight, so a burst of pushes collapses
 * into one run and the next.
 */
@RestController
@ConditionalOnProperty(name = "cp.gitops.enabled", havingValue = "true")
@Slf4j
public class GitOpsWebhookController {

    private final ReconcileCatalogueUseCase reconcile;
    private final String secret;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "gitops-reconcile");
        t.setDaemon(true);
        return t;
    });

    public GitOpsWebhookController(ReconcileCatalogueUseCase reconcile,
                                   @Value("${cp.gitops.webhook-secret:}") String secret) {
        this.reconcile = reconcile;
        this.secret = secret;
    }

    @PostMapping("/cp-webhooks/github")
    public ResponseEntity<String> github(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] body) {

        if (!verified(signature, body == null ? new byte[0] : body)) {
            log.warn("Rejected a GitOps webhook: signature did not verify.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("bad signature");
        }
        if ("ping".equals(event)) {
            return ResponseEntity.ok("pong");
        }
        if (event != null && !"push".equals(event)) {
            // Not a push — a star, a fork, whatever GitHub also sends. Acknowledged, ignored.
            return ResponseEntity.accepted().body("ignored " + event);
        }
        worker.execute(() -> reconcile.reconcile("webhook"));
        return ResponseEntity.accepted().body("reconcile queued");
    }

    /**
     * True when the body's HMAC-SHA256 under the shared secret matches the {@code sha256=…} GitHub
     * sent. A blank secret returns true and verifies nothing — the documented, logged escape hatch,
     * the same one the engine's webhooks have.
     */
    private boolean verified(String signature, byte[] body) {
        if (secret == null || secret.isBlank()) {
            log.warn("GitOps webhook secret is not set — accepting the call without verifying it. "
                    + "Set cp.gitops.webhook-secret to close this.");
            return true;
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
            // Constant-time: a length-and-content compare here would leak how much of a forged
            // signature was right.
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Could not verify GitOps webhook signature: {}", e.toString());
            return false;
        }
    }
}
