package io.mateu.ecdemo1.communication.rest;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.send.WebPushCrypto;
import io.mateu.ecdemo1.communication.store.PushSubscription;
import io.mateu.ecdemo1.communication.store.PushSubscriptionRepository;
import io.mateu.ecdemo1.communication.ui.inbox.Caller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Map;

/**
 * Web Push for the consoles: the script every shell loads, the service worker that shows what
 * arrives, and where a browser registers. The two scripts are the only public paths under /_inbox
 * (the gateway lets them through without a token: a browser loads a script or a service worker
 * without one); the rest needs the signed-in user, whose roles decide what their browser receives.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PushController {

    public record Keys(String p256dh, String auth) {
    }

    public record Subscription(String endpoint, Keys keys) {
    }

    final CommunicationProperties properties;
    final PushSubscriptionRepository subscriptions;
    final Clock clock;

    @GetMapping(value = "/_inbox/push/push.js", produces = "text/javascript")
    public String script() throws IOException {
        return resource("push/push.js");
    }

    @GetMapping(value = "/_inbox/push/sw.js", produces = "text/javascript")
    public String serviceWorker() throws IOException {
        return resource("push/sw.js");
    }

    @GetMapping("/_inbox/push/public-key")
    public ResponseEntity<Map<String, String>> publicKey() {
        if (!properties.push().configured()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(Map.of("publicKey", properties.push().publicKey()));
    }

    /** The browser the signed-in user allowed: it receives what enters the inbox of their roles. Refreshed on every load. */
    @PostMapping("/_inbox/push/subscriptions")
    public ResponseEntity<Void> subscribe(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestBody Subscription subscription) {
        var username = Caller.username(authorization);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (subscription.endpoint() == null || !subscription.endpoint().startsWith("https://") || subscription.keys() == null
                || subscription.keys().p256dh() == null || subscription.keys().auth() == null
                || WebPushCrypto.unb64(subscription.keys().p256dh()).length != 65) {
            return ResponseEntity.badRequest().build();
        }
        var id = id(subscription.endpoint());
        var s = subscriptions.findById(id).orElseGet(PushSubscription::new);
        var created = s.id == null;
        s.id = id;
        s.endpoint = subscription.endpoint();
        s.p256dh = subscription.keys().p256dh();
        s.auth = subscription.keys().auth();
        s.username = username;
        s.roles = String.join(",", Caller.roles(authorization));
        if (created) {
            s.subscribedAt = clock.instant();
            log.info("{} allowed notifications in a browser (roles {})", username, s.roles);
        }
        subscriptions.save(s);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/_inbox/push/subscriptions")
    public ResponseEntity<Void> unsubscribe(@RequestParam String endpoint) {
        subscriptions.deleteById(id(endpoint));
        return ResponseEntity.noContent().build();
    }

    static String id(String endpoint) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return WebPushCrypto.b64(digest).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String resource(String path) throws IOException {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
