package io.mateu.ecdemo1.integrations.lifecycle;

import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import io.mateu.ecdemo1.integrations.store.IntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks at every onboarding's gate and, when what it waits for has happened, sends the message that
 * opens it. The engine does not keep a message nobody waits for, and the process may not have
 * reached its wait yet, so the message is sent again on every look until the next step moves the
 * gate on — a message for a gate already passed matches nothing and is dropped.
 *
 * <p>Gates that depend on something outside — the property's catalogue, the partners, the gaps — are
 * also looked at again every so often, besides when a person asks for it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Gates {

    final IntegrationRepository integrations;
    final Integrations lifecycle;
    final IntegrationsProperties properties;
    final Clock clock;
    final Map<String, Instant> lastRecheck = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${integrations.gate-check:5s}")
    public void look() {
        for (var waiting : integrations.findByGateIsNotNull()) {
            try {
                var last = lastRecheck.get(waiting.id);
                if (last == null || last.plus(properties.recheck()).isBefore(clock.instant())) {
                    lastRecheck.put(waiting.id, clock.instant());
                    lifecycle.recheckAutomatically(waiting.id);
                }
                lifecycle.signalGate(waiting.id);
            } catch (RuntimeException e) {
                log.warn("Gate of integration {} ({}) not looked at: {}", waiting.id, waiting.crsHotelCode, e.getMessage());
            }
        }
    }
}
