package io.mateu.ecdemo1.communication.rest;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.send.TestPushes;
import io.mateu.ecdemo1.communication.send.WebPushCrypto;
import io.mateu.ecdemo1.communication.store.PushSubscription;
import io.mateu.ecdemo1.communication.store.PushSubscriptionRepository;
import io.mateu.ecdemo1.communication.ui.inbox.Caller;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * without one); the rest needs the signed-in user: the recipients that push to them — by name or by
 * one of their roles — decide what their browser receives.
 *
 * <p>A browser that subscribes from the front office's host is the front desk's: it is told only what
 * the recipients push to the desk. Which host it is comes from the Origin the browser sends with the
 * POST — the gateway forwards it untouched — or, without one, from X-Forwarded-Host.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PushController {

    /*
     * What the browser posts is PushSubscription.toJSON(): endpoint, keys — and expirationTime, which
     * nothing here needs. This application's ObjectMapper fails on a field it does not know, so every
     * one of these ignores what it does not use: without that, every browser's subscription was a 400
     * and no browser was ever registered.
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Keys(String p256dh, String auth) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Subscription(String endpoint, Keys keys) {
    }

    /** Which browser to send the test to: its subscription's endpoint. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TestRequest(String endpoint) {
    }

    final CommunicationProperties properties;
    final PushSubscriptionRepository subscriptions;
    final Clock clock;
    final TestPushes tests;

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

    /** The browser the signed-in user allowed, with who they are and their roles. Refreshed on every load. */
    @PostMapping("/_inbox/push/subscriptions")
    public ResponseEntity<Void> subscribe(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestHeader(value = "Origin", required = false) String origin,
                                          @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost,
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
        s.app = frontDesk(origin, forwardedHost) ? PushSubscription.FRONT_DESK : null;
        if (created) {
            s.subscribedAt = clock.instant();
            log.info("{} allowed notifications in a browser{} (roles {})", username, s.atFrontDesk() ? " at the front desk" : "", s.roles);
        }
        subscriptions.save(s);
        return ResponseEntity.noContent().build();
    }

    /** The person turned notifications off in this browser. Only their own browser: nobody else's. */
    @DeleteMapping("/_inbox/push/subscriptions")
    public ResponseEntity<Void> unsubscribe(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestParam String endpoint) {
        var username = Caller.username(authorization);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        subscriptions.findById(id(endpoint))
                .filter(s -> username.equals(s.username))
                .ifPresent(s -> {
                    subscriptions.delete(s);
                    log.info("{} turned notifications off in a browser", username);
                });
        return ResponseEntity.noContent().build();
    }

    /**
     * "Enviarme una prueba": a notification to this browser, if it is the caller's. 200 with what
     * happened — the push service took it, or why not — so the page can say it.
     */
    @PostMapping("/_inbox/push/test")
    public ResponseEntity<TestPushes.Result> test(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @RequestBody TestRequest request) {
        var username = Caller.username(authorization);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request == null || request.endpoint() == null || request.endpoint().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(tests.toBrowser(username, id(request.endpoint())));
    }

    /** Whether the browser that calls is on one of the front office's hosts. */
    boolean frontDesk(String origin, String forwardedHost) {
        var host = host(origin);
        if (host == null && forwardedHost != null && !forwardedHost.isBlank()) {
            host = forwardedHost.split(",")[0].trim().toLowerCase(java.util.Locale.ROOT);
            host = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
        }
        return host != null && properties.push().frontDeskHosts().contains(host);
    }

    static String host(String origin) {
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            return null;
        }
        try {
            var host = java.net.URI.create(origin.trim()).getHost();
            return host == null ? null : host.toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
