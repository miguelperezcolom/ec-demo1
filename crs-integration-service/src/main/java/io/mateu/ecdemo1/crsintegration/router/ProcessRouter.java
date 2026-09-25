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
    final Integrations integrations;

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
        var hotelCode = hotelOf(event);
        if (hotelCode != null && !integrations.integrated(hotelCode)) {
            log.info("No integration for hotel {}: {} {} is not projected", hotelCode, typeOf(event), event.key());
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

    /**
     * Starts «Proyectar Reserva» for a reservation nobody changed: the backfill's way in. The
     * version is left out — the process reads the reservation as it is now — and the origin goes
     * with it, which is how the preparation knows not to hold it for the activation.
     */
    @Transactional
    public void project(String hotelCode, String locator, String origin) {
        var definitionId = io.mateu.ecdemo1.integration.model.process.Definitions.PROJECT_RESERVATION;
        var processKey = definitionId + ":" + hotelCode + "/" + locator + ":" + origin;
        if (!inbox.firstTime("backfill", processKey)) {
            return;
        }
        var variables = new ArrayList<>(List.of(
                new Variable(ProcessVariables.DEFINITION_ID, definitionId),
                new Variable(ProcessVariables.PROCESS_KEY, processKey),
                new Variable(ProcessVariables.EVENT_ID, origin),
                new Variable(ProcessVariables.ORIGIN, origin)));
        reservation(variables, hotelCode, locator);
        outbox.appendToEngine(new ProcessCreationRequested(definitionId, processKey, variables, null,
                AuthorizationContext.SYSTEM));
    }

    /**
     * «Registrar no-show» (HLA F006, #5): the hotel says a reservation's guests did not arrive. One
     * process per reservation — told twice, it is one no-show; the CRS applies its rule, and the result
     * comes back down to the PMS as the cancellation it is.
     */
    public boolean noShow(String hotelCode, String locator, String reportedBy) {
        var definitionId = io.mateu.ecdemo1.integration.model.process.Definitions.REGISTER_NO_SHOW;
        var processKey = definitionId + ":" + hotelCode + "/" + locator;
        if (!inbox.firstTime("no-show", processKey)) {
            return false;
        }
        var variables = new ArrayList<>(List.of(
                new Variable(ProcessVariables.DEFINITION_ID, definitionId),
                new Variable(ProcessVariables.PROCESS_KEY, processKey),
                new Variable("bookingId", locator),
                new Variable("reportedBy", reportedBy == null ? "front office" : reportedBy)));
        reservation(variables, hotelCode, locator);
        outbox.appendToEngine(new ProcessCreationRequested(definitionId, processKey, variables, null,
                AuthorizationContext.SYSTEM));
        return true;
    }

    private static void reservation(List<Variable> variables, String hotelCode, String locator) {
        variables.add(new Variable(ProcessVariables.HOTEL_CODE, hotelCode));
        variables.add(new Variable(ProcessVariables.LOCATOR, locator));
    }

    /** The hotel a reservation event belongs to; null for a chain-wide one, as a partner is. */
    static String hotelOf(IntegrationEvent event) {
        return switch (event) {
            case ReservationCreated e -> e.hotelCode();
            case ReservationModified e -> e.hotelCode();
            case ReservationCancelled e -> e.hotelCode();
            case PartnerChanged e -> null;
        };
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
