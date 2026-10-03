package io.mateu.ecdemo1.pmsintegration.worker;

import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.pmsintegration.config.PmsIntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Keeps retrying, and also tells someone" (HLA, política de fallo): the engine retries a failing
 * step without limit; this notices when a step has been failing longer than the threshold and asks
 * for a notification, once, while the retrying goes on.
 *
 * <p>In memory: after a restart the clock starts again, which delays an alert and never loses the
 * work. The work is the engine's; this is only the alarm.
 */
@Component
@Slf4j
public class RetryWatch {

    record Failing(Instant since, boolean alerted, String subject) {
    }

    final PmsIntegrationProperties properties;
    final StreamBridge streamBridge;
    final Clock clock;
    final Map<String, Failing> failing = new ConcurrentHashMap<>();
    /** The threshold, changeable at runtime (the demo page); the configured one by default. */
    final io.mateu.ecdemo1.pmsintegration.config.RetryAlert alert;

    public RetryWatch(PmsIntegrationProperties properties, StreamBridge streamBridge, Clock clock) {
        this(properties, streamBridge, clock, new io.mateu.ecdemo1.pmsintegration.config.RetryAlert(properties.alertAfter()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RetryWatch(PmsIntegrationProperties properties, StreamBridge streamBridge, Clock clock,
                      io.mateu.ecdemo1.pmsintegration.config.RetryAlert alert) {
        this.properties = properties;
        this.streamBridge = streamBridge;
        this.clock = clock;
        this.alert = alert;
    }

    public void failed(String processId, String stepId, String hotelCode, String subject, String reason) {
        var key = processId + "/" + stepId;
        var now = clock.instant();
        var state = failing.merge(key, new Failing(now, false, subject), (old, fresh) -> old);
        if (!state.alerted() && state.since().plus(alert.after()).isBefore(now)) {
            failing.put(key, new Failing(state.since(), true, subject));
            var sent = streamBridge.send("notifications", new NotificationRequested(UUID.randomUUID().toString(),
                    NotificationType.RETRYING_TOO_LONG, hotelCode, subject,
                    ("project-stay".equals(stepId) ? "Projecting %s to the front office keeps failing"
                            : "Writing %s to the PMS keeps failing").formatted(subject),
                    "Step %s has been failing since %s and is still being retried. Last error: %s"
                            .formatted(stepId, state.since(), reason),
                    null, "retrying:" + key, now));
            log.warn("{} failing since {}: alert {}", key, state.since(), sent ? "sent" : "NOT sent");
        }
    }

    public void succeeded(String processId, String stepId) {
        var state = failing.remove(processId + "/" + stepId);
        if (state != null && state.alerted() && state.subject() != null) {
            // It was alerted as failing; it went through: the alert is done with.
            streamBridge.send("notificationResolutions", new io.mateu.ecdemo1.integration.model.notification.NotificationResolved(
                    state.subject(), "retry", clock.instant()));
        }
    }
}
