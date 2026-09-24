package io.mateu.ecdemo1.communication.send;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.store.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Google Chat, for what is urgent: a message to the space whose incoming webhook is configured. The
 * webhook's URL carries its key and token — it is a secret, and comes from one.
 */
@Slf4j
@Component
public class GoogleChat {

    final CommunicationProperties properties;
    final RestClient rest;

    public GoogleChat(CommunicationProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        var webhook = properties.chat().webhook();
        return webhook != null && !webhook.isBlank();
    }

    /** Posts the notification; throws if Google Chat does not take it. */
    public void post(Notification n) {
        rest.post().uri(URI.create(properties.chat().webhook()))
                .contentType(new MediaType(MediaType.APPLICATION_JSON, java.nio.charset.StandardCharsets.UTF_8))
                .body(Map.of("text", text(n)))
                .retrieve().toBodilessEntity();
    }

    /** Chat's own light markup: *bold*, and a link as <url|label>. */
    static String text(Notification n) {
        var text = new StringBuilder("🚨 *").append(n.title).append("*");
        if (n.hotelCode != null) {
            text.append(" · ").append(n.hotelCode);
        }
        text.append("\n").append(n.body == null ? "" : n.body);
        if (n.link != null && !n.link.isBlank()) {
            text.append("\n<").append(n.link).append("|Open>");
        }
        return text.toString();
    }
}
