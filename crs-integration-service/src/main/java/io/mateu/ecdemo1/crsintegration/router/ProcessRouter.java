package io.mateu.ecdemo1.crsintegration.router;

import io.mateu.ecdemo1.crsintegration.config.CrsProperties;
import io.mateu.ecdemo1.crsintegration.inbox.Inbox;
import io.mateu.ecdemo1.crsintegration.outbox.Outbox;
import io.mateu.ecdemo1.integration.model.events.IntegrationEvent;
import io.mateu.ecdemo1.integration.model.events.PartnerChanged;
import io.mateu.ecdemo1.integration.model.events.ReservationCancelled;
import io.mateu.ecdemo1.integration.model.events.ReservationCreated;
import io.mateu.ecdemo1.integration.model.events.ReservationModified;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.security.AuthorizationContext;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The seam between business events and processes: the engine understands its own events, not the
 * integration's, so this turns each business event into the start of the process its route names.
 *
 * <p>One process per event, and no check for one already running for the same reservation: the
 * HLA's "una instancia por evento". The extra instances are harmless — each reads the current state
 * and the PMS write is ordered by version — and checking first would reopen the race between "one
 * is alive" and "signal it" in which a change is lost in silence.
 *
 * <p>The business key is unique per event and deterministic, so the start request can be repeated
 * without starting the process twice. The process carries references, never the reservation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessRouter {

    static final String CONSUMER = "router";

    final Inbox inbox;
    final Outbox outbox;
    final CrsProperties properties;

    @Transactional
    public void route(IntegrationEvent event) {
        if (!inbox.firstTime(CONSUMER, event.eventId())) {
            return;
        }
        var definitionId = properties.routes().get(typeOf(event));
        if (definitionId == null) {
            log.info("No process for {} {}", typeOf(event), event.eventId());
            return;
        }
        var processKey = definitionId + ":" + event.key() + ":" + event.eventId();
        var variables = new ArrayList<>(List.of(
                new Variable(ProcessVariables.DEFINITION_ID, definitionId),
                new Variable(ProcessVariables.PROCESS_KEY, processKey),
                new Variable(ProcessVariables.EVENT_ID, event.eventId()),
                new Variable(ProcessVariables.VERSION, String.valueOf(event.version()))));
        switch (event) {
            case ReservationCreated e -> reservation(variables, e.hotelCode(), e.locator());
            case ReservationModified e -> reservation(variables, e.hotelCode(), e.locator());
            case ReservationCancelled e -> reservation(variables, e.hotelCode(), e.locator());
            case PartnerChanged e -> variables.add(new Variable(ProcessVariables.PARTNER_CODE, e.partnerCode()));
        }
        log.info("Starting {} for {}", definitionId, event.key());
        outbox.appendToEngine(new ProcessCreationRequested(definitionId, processKey, variables, null,
                AuthorizationContext.SYSTEM));
    }

    private static void reservation(List<Variable> variables, String hotelCode, String locator) {
        variables.add(new Variable(ProcessVariables.HOTEL_CODE, hotelCode));
        variables.add(new Variable(ProcessVariables.LOCATOR, locator));
    }

    static String typeOf(IntegrationEvent event) {
        return switch (event) {
            case ReservationCreated e -> "reservation-created";
            case ReservationModified e -> "reservation-modified";
            case ReservationCancelled e -> "reservation-cancelled";
            case PartnerChanged e -> "partner-changed";
        };
    }
}
