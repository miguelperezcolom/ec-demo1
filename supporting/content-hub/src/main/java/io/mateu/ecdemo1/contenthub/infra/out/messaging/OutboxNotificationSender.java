package io.mateu.ecdemo1.contenthub.infra.out.messaging;

import io.mateu.ecdemo1.contenthub.application.out.NotificationLog;
import io.mateu.ecdemo1.contenthub.application.out.NotificationSender;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationResolved;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.messaging.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The platform's own contract: {@code notifications} and {@code notification-resolutions}, which
 * communication-service reads and turns into the bell and Web Push. Addressed to the reader: the
 * recipients table says who looks after a hotel, not who reads what.
 */
@Component
@RequiredArgsConstructor
public class OutboxNotificationSender implements NotificationSender {

    static final String NOTIFICATIONS = "notifications";
    static final String RESOLUTIONS = "notification-resolutions";

    final Outbox outbox;

    @Override
    public void send(ReaderNotification n) {
        var message = new NotificationRequested(UUID.randomUUID().toString(), type(n.kind()), null, n.subject(), n.title(),
                n.body(), n.link(), n.dedupKey(), n.at(), List.of(n.userId()));
        outbox.append(NOTIFICATIONS, n.dedupKey(), "NotificationRequested", Json.write(message), Map.of());
    }

    @Override
    public void resolve(String subject, String resolvedBy, java.time.Instant at) {
        outbox.append(RESOLUTIONS, subject, "NotificationResolved",
                Json.write(new NotificationResolved(subject, resolvedBy, at)), Map.of());
    }

    static NotificationType type(NotificationLog.Kind kind) {
        return kind == NotificationLog.Kind.PUBLISHED ? NotificationType.INTRANET_PUBLISHED
                : NotificationType.INTRANET_CONFIRMATION_DUE;
    }
}
