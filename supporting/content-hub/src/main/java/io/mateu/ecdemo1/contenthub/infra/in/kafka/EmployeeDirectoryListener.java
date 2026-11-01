package io.mateu.ecdemo1.contenthub.infra.in.kafka;

import io.mateu.ecdemo1.contenthub.application.directory.DirectoryUpdates;
import io.mateu.ecdemo1.contenthub.application.directory.EmployeeMessage;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import io.mateu.ecdemo1.messaging.Inbox;
import io.mateu.ecdemo1.messaging.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * The users service's employee-directory: each message taken once (inbox, by its eventId) in the same
 * transaction as its effect on the directory. With an empty directory at startup, a full copy is asked
 * for on employee-directory-requests — through the outbox, like everything this service sends.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmployeeDirectoryListener {

    static final String CONSUMER = "employee-directory";
    public static final String REQUESTS = "employee-directory-requests";

    final DirectoryUpdates updates;
    final Inbox inbox;
    final Outbox outbox;
    final EmployeeDirectory directory;
    final TransactionTemplate transactions;

    @KafkaListener(topics = "employee-directory", groupId = "ec-demo1-content-hub-directory",
            autoStartup = "${content-hub.kafka.enabled:true}")
    public void on(byte[] payload) {
        EmployeeMessage message;
        try {
            message = Json.read(new String(payload, StandardCharsets.UTF_8), EmployeeMessage.class);
        } catch (RuntimeException e) {
            log.error("Unreadable employee-directory message, dropped: {}", new String(payload, StandardCharsets.UTF_8), e);
            return;
        }
        inbox.once(message.eventId(), CONSUMER, () -> updates.apply(message));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void askForACopyIfEmpty() {
        if (directory.count() == 0) {
            requestSnapshot();
        }
    }

    public void requestSnapshot() {
        transactions.executeWithoutResult(s -> outbox.append(REQUESTS, "content-hub",
                Json.write(Map.of("requestId", UUID.randomUUID().toString(), "requestedBy", "content-hub"))));
        log.info("Asked the users service for a copy of the employee directory");
    }
}
