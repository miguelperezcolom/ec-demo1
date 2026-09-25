package io.mateu.ecdemo1.communication.send;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.store.DeliveryStatus;
import io.mateu.ecdemo1.communication.store.Notification;
import io.mateu.ecdemo1.communication.store.NotificationRepository;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * The integration decides what to tell and about which hotel; this decides who, and tells them —
 * by e-mail, through the SMTP relay. A notification whose key was already seen is not sent again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Deliveries {

    final NotificationRepository notifications;
    final RecipientRepository recipients;
    final JavaMailSender mail;
    final CommunicationProperties properties;
    final Clock clock;
    final io.mateu.ecdemo1.communication.inbox.Inbox inbox;

    @Transactional
    public void accept(NotificationRequested request) {
        if (notifications.existsByDedupKey(request.dedupKey())) {
            log.debug("Already notified: {}", request.dedupKey());
            return;
        }
        var n = new Notification();
        n.id = request.notificationId();
        n.type = request.type();
        n.hotelCode = request.hotelCode();
        n.subject = request.subject();
        n.title = request.title();
        n.body = request.body();
        n.link = request.link();
        n.dedupKey = request.dedupKey();
        n.requestedAt = request.requestedAt();
        notifications.save(n);
        // Everything goes to the inbox of the roles that see to it — and from there to the chat spaces
        // and the browsers (Announcements); only what cannot wait is emailed too.
        inbox.post(request);
        if (properties.inbox().isUrgent(n.type.name())) {
            deliver(n);
        } else {
            n.status = DeliveryStatus.INBOX_ONLY;
            notifications.save(n);
        }
    }

    /** Sends a notification again, to whoever should receive it now. */
    @Transactional
    public Notification resend(String id) {
        var n = notifications.findById(id).orElseThrow(() -> new NoSuchElementException("No notification " + id));
        deliver(n);
        return n;
    }

    @Scheduled(fixedDelayString = "${communication.retry-every:PT2M}")
    @Transactional
    public void retryFailed() {
        notifications.findByStatusAndAttemptsLessThan(DeliveryStatus.FAILED, properties.maxAttempts()).forEach(this::deliver);
    }

    void deliver(Notification n) {
        var to = recipients.findAll().stream().filter(r -> r.wants(n.type, n.hotelCode)).map(Recipient::getEmail)
                .distinct().toList();
        n.recipients = String.join(", ", to);
        if (to.isEmpty()) {
            n.status = DeliveryStatus.NO_RECIPIENTS;
            log.warn("Nobody receives {} for hotel {}: '{}'", n.type, n.hotelCode, n.title);
            notifications.save(n);
            return;
        }
        n.attempts++;
        try {
            var message = new SimpleMailMessage();
            message.setFrom(properties.from());
            message.setTo(to.toArray(String[]::new));
            message.setSubject("[%s]%s %s".formatted(n.type, n.hotelCode == null ? "" : " " + n.hotelCode, n.title));
            message.setText(n.body + (n.link == null ? "" : "\n\n" + n.link));
            mail.send(message);
            n.status = DeliveryStatus.SENT;
            n.sentAt = clock.instant();
            n.lastError = null;
            log.info("Notified {} to {}: {}", n.type, to.stream().collect(Collectors.joining(",")), n.title);
        } catch (RuntimeException e) {
            n.status = DeliveryStatus.FAILED;
            n.lastError = e.getMessage();
            log.warn("Could not send {} (attempt {}): {}", n.id, n.attempts, e.getMessage());
        }
        notifications.save(n);
    }
}
