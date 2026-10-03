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
        if (inbox == null) inbox = new Inbox(null, null);
        if (chat == null) chat = new Chat(null);
        if (push == null) push = new Push(null, null, null, null);
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
     * @param frontDeskHosts the front office's hosts: a browser that subscribes from one of them is at
     *                   the front desk, and is told only what the recipients push to the desk
     */
    public record Push(String publicKey, String privateKey, String subject, List<String> frontDeskHosts) {

        public Push {
            frontDeskHosts = frontDeskHosts == null ? List.of()
                    : frontDeskHosts.stream().filter(h -> h != null && !h.isBlank()).map(h -> h.trim().toLowerCase()).toList();
        }

        public boolean configured() {
            return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
        }
    }

    /**
     * @param tasksLink where a person fills in the forms engine's tasks
     * @param taskRoles the roles a form's tasks go to when the forms engine announces none, by form id
     *                  ({@code confirmar-reset-demo=ai-admin;other-form=a|b}). The forms engine with JPA
     *                  persistence drops a form's requiredRoles before EventConductor's fix (forms V7):
     *                  without this, reset-demo's confirmation would reach everyone's inbox
     */
    public record Inbox(String tasksLink, String taskRoles) {

        /** The roles of a form's tasks when the forms engine says none; empty when nothing is set. */
        public java.util.List<String> rolesOf(String formId) {
            if (formId == null || taskRoles == null) {
                return java.util.List.of();
            }
            for (var entry : taskRoles.split(";")) {
                var parts = entry.split("=", 2);
                if (parts.length == 2 && parts[0].trim().equals(formId)) {
                    return java.util.Arrays.stream(parts[1].split("\\|")).map(String::trim).filter(r -> !r.isEmpty()).toList();
                }
            }
            return java.util.List.of();
        }

        public Inbox {
            if (tasksLink == null) tasksLink = "/forms/tasks";
        }
    }
}
