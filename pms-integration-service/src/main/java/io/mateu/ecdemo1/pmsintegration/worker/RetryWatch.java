package io.mateu.ecdemo1.pmsintegration.worker;

import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.pmsintegration.config.PmsIntegrationProperties;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Slf4j
public class RetryWatch {

    record Failing(Instant since, boolean alerted, String subject) {
    }

    final PmsIntegrationProperties properties;
    final StreamBridge streamBridge;
    final Clock clock;
    final Map<String, Failing> failing = new ConcurrentHashMap<>();

    public void failed(TaskExecutionRequested task, String hotelCode, String subject, String reason) {
        var key = task.processId() + "/" + task.stepId();
        var now = clock.instant();
        var state = failing.merge(key, new Failing(now, false, subject), (old, fresh) -> old);
        if (!state.alerted() && state.since().plus(properties.alertAfter()).isBefore(now)) {
            failing.put(key, new Failing(state.since(), true, subject));
            var sent = streamBridge.send("notifications", new NotificationRequested(UUID.randomUUID().toString(),
                    NotificationType.RETRYING_TOO_LONG, hotelCode, subject,
                    "Writing %s to the PMS keeps failing".formatted(subject),
                    "Step %s has been failing since %s and is still being retried. Last error: %s"
                            .formatted(task.stepId(), state.since(), reason),
                    null, "retrying:" + key, now));
            log.warn("{} failing since {}: alert {}", key, state.since(), sent ? "sent" : "NOT sent");
        }
    }

    public void succeeded(TaskExecutionRequested task) {
        var state = failing.remove(task.processId() + "/" + task.stepId());
        if (state != null && state.alerted() && state.subject() != null) {
            // It was alerted as failing; it went through: the alert is done with.
            streamBridge.send("notificationResolutions", new io.mateu.ecdemo1.integration.model.notification.NotificationResolved(
                    state.subject(), "retry", clock.instant()));
        }
    }
}
