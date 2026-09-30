package io.mateu.ecdemo1.communication.alerts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mateu.ecdemo1.communication.inbox.Inbox;
import io.mateu.ecdemo1.communication.send.Deliveries;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The platform's alerts, as Alertmanager sends them (its webhook, payload version 4), turned into what
 * this service already does with an integration's notifications: a firing alert is a notification —
 * PLATFORM_ALERT, or PLATFORM_ALERT_CRITICAL for severity critical — that the recipients table routes
 * (the integration's administrators' inbox and browsers, out of the box); a resolved one closes it in
 * every inbox it reached.
 *
 * <p>One alert is its fingerprint — Alertmanager's hash of its labels — and one episode of it is the
 * fingerprint and when it started: the same episode sent again (every repeat_interval, after a restart
 * of Alertmanager) is the same notification, deduped by its key; the alert firing again after it
 * resolved is a new one. The subject is the fingerprint alone, so resolving closes whatever of it is
 * open.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAlerts {

    /** Alertmanager's webhook body; what is not used here is ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Webhook(String version, String status, String receiver, List<Alert> alerts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alert(String status, Map<String, String> labels, Map<String, String> annotations,
                        Instant startsAt, Instant endsAt, String generatorURL, String fingerprint) {
    }

    /** What one webhook call did. */
    public record Outcome(int notified, int resolved, int ignored) {
    }

    static final String BY = "alertmanager";

    final Deliveries deliveries;
    final Inbox inbox;
    final Clock clock;

    public Outcome received(Webhook webhook) {
        int notified = 0, resolved = 0, ignored = 0;
        for (var alert : webhook == null || webhook.alerts() == null ? List.<Alert>of() : webhook.alerts()) {
            var status = alert.status() == null ? webhook.status() : alert.status();
            if ("firing".equals(status)) {
                deliveries.accept(notification(alert));
                notified++;
            } else if ("resolved".equals(status)) {
                var at = alert.endsAt() == null || alert.endsAt().getEpochSecond() <= 0 ? clock.instant() : alert.endsAt();
                inbox.resolve(subject(alert), BY, at);
                resolved++;
            } else {
                log.warn("Alert {} with status '{}' ignored", name(alert), status);
                ignored++;
            }
        }
        return new Outcome(notified, resolved, ignored);
    }

    NotificationRequested notification(Alert alert) {
        var labels = alert.labels() == null ? Map.<String, String>of() : alert.labels();
        var annotations = alert.annotations() == null ? Map.<String, String>of() : alert.annotations();
        var critical = "critical".equalsIgnoreCase(labels.get("severity"));
        var startsAt = alert.startsAt() == null ? clock.instant() : alert.startsAt();
        var key = subject(alert) + "/" + startsAt.toEpochMilli();
        var title = annotations.getOrDefault("summary", name(alert));
        var body = annotations.getOrDefault("description", "");
        var link = annotations.getOrDefault("link", alert.generatorURL());
        return new NotificationRequested(
                UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString(),
                critical ? NotificationType.PLATFORM_ALERT_CRITICAL : NotificationType.PLATFORM_ALERT,
                labels.get("hotel"),
                subject(alert),
                cut((critical ? "🔴 " : "🟠 ") + title, 500),
                cut(body, 4000),
                cut(link, 1000),
                key,
                startsAt);
    }

    /** The alert, whatever its episode: Alertmanager's fingerprint, or its labels when it sent none. */
    static String subject(Alert alert) {
        if (alert.fingerprint() != null && !alert.fingerprint().isBlank()) {
            return "alert/" + alert.fingerprint();
        }
        var labels = new TreeMap<>(alert.labels() == null ? Map.<String, String>of() : alert.labels());
        return "alert/" + UUID.nameUUIDFromBytes(labels.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String name(Alert alert) {
        return alert.labels() == null ? "alert" : alert.labels().getOrDefault("alertname", "alert");
    }

    static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
