package io.mateu.ecdemo1.communication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * @param inbox who sees what in the inbox, and what is urgent enough to be emailed as well
 * @param chat  the chat space the urgent notifications are also posted to
 */
@ConfigurationProperties("communication")
public record CommunicationProperties(String from, String defaultEmail, int maxAttempts, Inbox inbox, Chat chat) {

    public CommunicationProperties {
        if (from == null) from = "integration@ec1.mateu.io";
        if (maxAttempts <= 0) maxAttempts = 5;
        if (inbox == null) inbox = new Inbox(null, null, null, null);
        if (chat == null) chat = new Chat(null);
    }

    /** @param webhook a Google Chat space's incoming webhook; blank, no chat */
    public record Chat(String webhook) {
    }

    /**
     * @param defaultRoles the roles whose inbox a kind of notification goes to when {@code roles} does
     *                     not name it
     * @param roles        by notification type, the roles that see to it
     * @param urgent       the types that are also emailed: everything goes to the inbox, and only
     *                     what cannot wait for someone to look at it goes by email too
     * @param tasksLink    where a person fills in the forms engine's tasks
     */
    public record Inbox(List<String> defaultRoles, Map<String, List<String>> roles, List<String> urgent, String tasksLink) {

        public Inbox {
            if (defaultRoles == null || defaultRoles.isEmpty()) defaultRoles = List.of("ai-admin");
            if (roles == null) roles = Map.of();
            if (urgent == null) urgent = List.of("PMS_REJECTED", "RETRYING_TOO_LONG");
            if (tasksLink == null) tasksLink = "/forms/tasks";
        }

        public List<String> rolesFor(String type) {
            var named = roles.get(type);
            return named == null || named.isEmpty() ? defaultRoles : named;
        }

        public boolean isUrgent(String type) {
            return urgent.contains(type);
        }
    }
}
