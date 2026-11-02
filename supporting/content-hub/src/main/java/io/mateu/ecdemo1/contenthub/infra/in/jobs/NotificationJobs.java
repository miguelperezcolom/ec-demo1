package io.mateu.ecdemo1.contenthub.infra.in.jobs;

import io.mateu.ecdemo1.contenthub.application.usecases.ItemNotifications;
import io.mateu.ecdemo1.contenthub.application.usecases.UsageAnalytics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The notifications' fan-out and reminders, and the usage's daily figures, by the clock. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationJobs {

    final ItemNotifications notifications;
    final UsageAnalytics analytics;

    @Scheduled(fixedDelayString = "${content-hub.jobs.notifications:PT30S}", initialDelayString = "PT40S")
    public void fanOut() {
        try {
            notifications.fanOut();
        } catch (RuntimeException e) {
            log.warn("Notifications pass failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${content-hub.jobs.reminders:PT15M}", initialDelayString = "PT2M")
    public void reminders() {
        try {
            notifications.remindDue();
            notifications.closeStale();
        } catch (RuntimeException e) {
            log.warn("Reminders pass failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${content-hub.jobs.analytics:PT15M}", initialDelayString = "PT1M")
    public void analytics() {
        try {
            analytics.aggregate();
        } catch (RuntimeException e) {
            log.warn("Usage aggregation failed: {}", e.getMessage());
        }
    }
}
