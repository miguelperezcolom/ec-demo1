package io.mateu.ecdemo1.communication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * How to reach people, not who hears of what: that is the recipients table, and only that.
 *
 * @param defaultEmail where the recipient seeded for urgent notifications e-mails, on a table that
 *                     starts empty
 * @param inbox        where the forms engine's tasks are filled in
 * @param chat         the Google Chat spaces recipients can post to
 * @param push         the VAPID keys Web Push is sent with; without them, no browser is notified
 */
@ConfigurationProperties("communication")
public record CommunicationProperties(String from, String defaultEmail, int maxAttempts, Inbox inbox, Chat chat, Push push) {

    public CommunicationProperties {
        if (from == null) from = "integration@ec1.mateu.io";
        if (maxAttempts <= 0) maxAttempts = 5;
        if (inbox == null) inbox = new Inbox(null);
        if (chat == null) chat = new Chat(null);
        if (push == null) push = new Push(null, null, null);
    }

    /**
     * @param webhooks the incoming webhooks of the Google Chat spaces, numbered from 1 in this order;
     *                 blank ones are left out
     */
    public record Chat(List<String> webhooks) {

        public Chat {
            webhooks = webhooks == null ? List.of() : webhooks.stream().filter(w -> w != null && !w.isBlank()).toList();
        }
    }

    /**
     * @param publicKey  the VAPID public key, uncompressed P-256, base64url — what the browser subscribes with
     * @param privateKey the VAPID private key, base64url; a secret
     * @param subject    who sends, for the push services: a mailto: or an https: URL
     */
    public record Push(String publicKey, String privateKey, String subject) {

        public boolean configured() {
            return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
        }
    }

    /** @param tasksLink where a person fills in the forms engine's tasks */
    public record Inbox(String tasksLink) {

        public Inbox {
            if (tasksLink == null) tasksLink = "/forms/tasks";
        }
    }
}
