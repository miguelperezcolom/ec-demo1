package io.mateu.ecdemo1.integration.model.notification;

import java.time.Instant;

/**
 * What a notification asked a person to see to is no longer waiting: the cause was resolved, the
 * integration moved past the gate, the write went through. Every notification about the same
 * {@code subject} requested before {@code resolvedAt} is closed — in every inbox it reached.
 *
 * @param subject    the {@link NotificationRequested#subject()} it resolves: a cause key,
 *                   {@code integration/<hotel>}, …
 * @param resolvedBy who or what resolved it, when it is known
 */
public record NotificationResolved(String subject, String resolvedBy, Instant resolvedAt) {
}
