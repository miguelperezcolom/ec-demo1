package io.mateu.ecdemo1.integrations.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * What this service consumes besides the engine's tasks, which are the worker runtime's
 * (worker.IntegrationsTasks).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StreamFunctions {

    final io.mateu.ecdemo1.integrations.frontoffice.PmsReservationEvents pmsReservations;
    final TolerantReader reader;
    final io.mateu.ecdemo1.integrations.frontoffice.ReceptionEvents reception;

    /**
     * What a front office's desk did ({@code front-office-events}): the PMS records it (pms-fo). One
     * that cannot be read is logged and skipped: the desk's state stays in the front office.
     */
    @Bean
    public Consumer<org.springframework.messaging.Message<byte[]>> consumeFrontOfficeEvents() {
        return message -> {
            io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent event;
            try {
                event = reader.mapper().readValue(message.getPayload(),
                        io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.class);
            } catch (java.io.IOException e) {
                log.error("Unreadable front office event, skipped: {}", new String(message.getPayload()), e);
                return;
            }
            reception.on(event);
        };
    }

    /**
     * The connector wrote a reservation into the PMS ({@code pms-reservations}): the front office fed
     * from that property takes it. One that cannot be read is logged and skipped: the polling brings it.
     */
    @Bean
    public Consumer<org.springframework.messaging.Message<byte[]>> consumePmsReservations() {
        return message -> {
            io.mateu.ecdemo1.integration.model.pms.PmsReservationChanged event;
            try {
                event = reader.mapper().readValue(message.getPayload(), io.mateu.ecdemo1.integration.model.pms.PmsReservationChanged.class);
            } catch (java.io.IOException e) {
                log.error("Unreadable PMS reservation event, skipped: {}", new String(message.getPayload()), e);
                return;
            }
            pmsReservations.on(event);
        };
    }
}
