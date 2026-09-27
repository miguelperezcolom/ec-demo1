package io.mateu.ecdemo1.communication.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.store.InboxItem;
import io.mateu.ecdemo1.communication.store.PushSubscription;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;

/** Sends an inbox item to one browser, through its push service. */
@Component
public class WebPush {

    /** The push service says the subscription no longer exists: the browser unsubscribed, or the permission went. */
    public static class Gone extends RuntimeException {
        public Gone(String message) {
            super(message);
        }
    }

    final CommunicationProperties properties;
    final ObjectMapper json;
    final Clock clock;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public WebPush(CommunicationProperties properties, ObjectMapper json, Clock clock) {
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    public boolean configured() {
        return properties.push().configured();
    }

    /** What the service worker shows: a title, a body, where a click takes the person, and whether it stays until seen. */
    public record Message(String title, String body, String url, String tag, boolean urgent) {
    }

    public void send(PushSubscription subscription, InboxItem item) {
        send(subscription, message(item));
    }

    public void send(PushSubscription subscription, Message message) {
        try {
            var body = WebPushCrypto.encrypt(json.writeValueAsBytes(message), WebPushCrypto.unb64(subscription.p256dh),
                    WebPushCrypto.unb64(subscription.auth));
            var push = properties.push();
            var request = HttpRequest.newBuilder(URI.create(subscription.endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .header("Urgency", message.urgent() ? "high" : "normal")
                    .header("Authorization", WebPushCrypto.vapidAuthorization(subscription.endpoint,
                            push.subject() == null || push.subject().isBlank() ? "mailto:integration@ec1.mateu.io" : push.subject(),
                            push.publicKey(), push.privateKey(), clock.instant().plus(Duration.ofHours(12)).getEpochSecond()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 410) {
                throw new Gone("The push service no longer has this subscription (" + response.statusCode() + ")");
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("The push service answered " + response.statusCode() + ": " + response.body());
            }
        } catch (Gone | IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted sending a push", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not send the push: " + e.getMessage(), e);
        }
    }

    /** An inbox item as the browser shows it: its hotel before the title, and the body cut to fit. */
    static Message message(InboxItem item) {
        var body = item.body == null ? "" : item.body;
        return new Message((item.hotelCode == null ? "" : item.hotelCode + " · ") + item.title,
                body.length() > 300 ? body.substring(0, 297) + "…" : body, item.link, item.id, item.urgent);
    }
}
