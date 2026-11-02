package io.mateu.ecdemo1.contenthub.infra.in.jobs;

import io.mateu.ecdemo1.contenthub.application.health.ContentHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * «Salud del contenido» every night (03:15 Madrid time by default). The day is claimed in the database, so a
 * second tick, a restart or another replica that night does nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HealthJobs {

    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    final ContentHealth health;
    final Clock clock;

    @Value("${content-hub.health.nightly.enabled:true}")
    boolean enabled;

    @Scheduled(cron = "${content-hub.health.nightly.cron:0 15 3 * * *}", zone = "Europe/Madrid")
    public void nightly() {
        if (!enabled) {
            return;
        }
        try {
            health.nightly(LocalDate.ofInstant(clock.instant(), ZONE));
        } catch (RuntimeException e) {
            log.warn("Content health nightly run failed: {}", e.getMessage());
        }
    }
}
