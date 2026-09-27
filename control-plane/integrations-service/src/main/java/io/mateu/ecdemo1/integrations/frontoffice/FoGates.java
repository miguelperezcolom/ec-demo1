package io.mateu.ecdemo1.integrations.frontoffice;

import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pms-fo onboardings' gates, looked at as {@link io.mateu.ecdemo1.integrations.lifecycle.Gates}
 * looks at the crs-pms ones: what depends on the outside — both ends answering, the front office
 * holding the catalogue sent — is looked at again every so often, and the message that opens a gate
 * is sent on every look until the next step moves it on.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FoGates {

    final FrontOfficeIntegrationRepository integrations;
    final FrontOfficeIntegrations lifecycle;
    final IntegrationsProperties properties;
    final Clock clock;
    final Map<String, Instant> lastRecheck = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${integrations.gate-check:5s}")
    public void look() {
        for (var waiting : integrations.findAll()) {
            if (!FrontOfficeIntegrations.waiting(waiting)) {
                continue;
            }
            try {
                var last = lastRecheck.get(waiting.id);
                if (last == null || last.plus(properties.recheck()).isBefore(clock.instant())) {
                    lastRecheck.put(waiting.id, clock.instant());
                    lifecycle.recheckAutomatically(waiting.id);
                }
                if (waiting.gate != null) {
                    lifecycle.signalGate(waiting.id);
                }
            } catch (RuntimeException e) {
                log.warn("Gate of front office integration {} ({}) not looked at: {}", waiting.id, waiting.pmsHotelCode, e.getMessage());
            }
        }
    }
}
