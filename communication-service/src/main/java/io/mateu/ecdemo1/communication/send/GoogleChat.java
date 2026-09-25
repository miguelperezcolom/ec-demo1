package io.mateu.ecdemo1.communication.send;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.store.InboxItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Google Chat: every item that enters an inbox is posted to each configured space, through its
 * incoming webhook. The webhook's URL carries its key and token — it is a secret, and comes from one.
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

    /** The spaces, numbered from 1 as the channels chat:1, chat:2 … name them. */
    public List<String> webhooks() {
        return properties.chat().webhooks();
    }

    /** Posts the item to the n-th space; throws if Google Chat does not take it. */
    public void post(int space, InboxItem item) {
        rest.post().uri(URI.create(webhooks().get(space - 1)))
                .contentType(new MediaType(MediaType.APPLICATION_JSON, java.nio.charset.StandardCharsets.UTF_8))
                .body(Map.of("text", text(item)))
                .retrieve().toBodilessEntity();
    }

    /** Chat's own light markup: *bold*, and a link as <url|label>. */
    static String text(InboxItem item) {
        var text = new StringBuilder(item.urgent ? "🚨 *" : "🔔 *").append(item.title).append("*");
        if (item.hotelCode != null) {
            text.append(" · ").append(item.hotelCode);
        }
        text.append("\n").append(item.body == null ? "" : item.body);
        if (item.link != null && !item.link.isBlank()) {
            text.append("\n<").append(item.link).append("|Open>");
        }
        return text.toString();
    }
}
