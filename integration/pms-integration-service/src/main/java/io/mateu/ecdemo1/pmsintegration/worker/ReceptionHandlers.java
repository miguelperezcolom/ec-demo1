package io.mateu.ecdemo1.pmsintegration.worker;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.Invoice;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.ReceptionOperation;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.process.Outcome;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.frontoffice.PmsEvents;
import io.mateu.ecdemo1.pmsintegration.frontoffice.ReceptionOutcomes;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaFrontDesk;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaReservations;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsRejectedException;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.worker.api.TaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * The reception, up to the PMS (pms-fo): the steps of «registrar-checkin», «registrar-checkout» and
 * «registrar-no-show-pms», one per task contract (ec-definitions, definitions/tasks; registered in
 * {@link PmsTasks}). The PMS is the master of the stay: what the desk did is recorded in Opera, and
 * what Opera then holds comes back to the front office by the stay's projection.
 *
 * <p>As the reservation steps: each reads Opera again, each is idempotent — it looks before it writes,
 * and a reservation already where the step would put it is not written (STALE) — and a refusal of
 * Opera is a cause the process waits on (WAIT), with an inbox notice, while the front office is told
 * «Opera: rechazado — motivo». Opera not answering propagates, and the engine retries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReceptionHandlers {

    /** Kafka's default message is 1 MB: a folio document bigger than this goes as its figures only. */
    static final int MAX_PDF_BYTES = 700_000;

    final OperaFrontDesk desk;
    final OperaReservations reservations;
    final IntegrationClients integration;
    final PmsEvents events;
    final ReceptionOutcomes outcomes;
    final Clock clock;

    /**
     * The input of the reception steps: the stay both ways — the front office's (hotel and stay), the
     * CRS's locator (or, for a reservation born in Opera, the Opera reservation under the Opera hotel),
     * and the Opera reservation when the front office had it linked.
     */
    public record ReceptionTask(String definitionId, String processKey, String hotelCode, String locator,
                                String pmsHotelCode, String pmsReservationId, String stayId, String roomNumber,
                                String eventId, String origin) {

        List<Variable> variables() {
            var variables = new ArrayList<Variable>();
            add(variables, ProcessVariables.DEFINITION_ID, definitionId);
            add(variables, ProcessVariables.PROCESS_KEY, processKey);
            add(variables, ProcessVariables.HOTEL_CODE, hotelCode);
            add(variables, ProcessVariables.LOCATOR, locator);
            add(variables, ProcessVariables.PMS_HOTEL_CODE, pmsHotelCode);
            add(variables, ProcessVariables.PMS_RESERVATION_ID, pmsReservationId);
            add(variables, ProcessVariables.STAY_ID, stayId);
            add(variables, ProcessVariables.ROOM_NUMBER, roomNumber);
            add(variables, ProcessVariables.EVENT_ID, eventId);
            add(variables, ProcessVariables.ORIGIN, origin);
            return variables;
        }

        /** Whether the locator is the CRS's (the Opera reservation is found by it) or Opera's own id. */
        boolean fromTheCrs() {
            return !pmsHotelCode.equals(hotelCode);
        }
    }

    /** {@code fetch-invoice@1}'s input. */
    public record InvoiceTask(String pmsHotelCode, String pmsReservationId, String stayId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RoomAssigned(String roomOutcome, String pmsReservationId, String roomNumber) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckedIn(String checkInOutcome, String pmsReservationId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckedOut(String checkOutOutcome, String pmsReservationId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NoShowRecorded(String noShowOutcome, String pmsReservationId) {
    }

    public record InvoiceFetched(String invoiceOutcome) {
    }

    /** The Opera reservation of the step, or empty having registered the wait for it to reach Opera. */
    Optional<JsonNode> reservation(ReceptionTask input, TaskContext task) {
        TaskHandlers.required(task, ProcessVariables.PROCESS_KEY, input.processKey());
        TaskHandlers.required(task, ProcessVariables.DEFINITION_ID, input.definitionId());
        var hotel = TaskHandlers.required(task, ProcessVariables.PMS_HOTEL_CODE, input.pmsHotelCode());
        TaskHandlers.required(task, ProcessVariables.HOTEL_CODE, input.hotelCode());
        TaskHandlers.required(task, ProcessVariables.LOCATOR, input.locator());
        Optional<JsonNode> found = Optional.empty();
        if (input.pmsReservationId() != null && !input.pmsReservationId().isBlank()) {
            found = desk.reservation(hotel, input.pmsReservationId());
        } else if (input.fromTheCrs()) {
            found = reservations.byLocator(hotel, input.locator());
        } else {
            found = desk.reservation(hotel, input.locator());
        }
        if (found.isEmpty()) {
            // A walk-in the desk checked in before its reservation reached Opera: the step waits for the
            // projection, which resolves this cause when it writes it (resolve-projection).
            await(input, List.of(Cause.notYetProjected(input.hotelCode(), input.locator())));
        }
        return found;
    }

    /**
     * The room the desk gave the guests, in Opera — unless Opera has it already. With no room chosen,
     * Opera's own assignment stands; with none, the first room Opera suggests. A room Opera refuses
     * (dirty, occupied, of another type, not the property's) is a cause.
     */
    public RoomAssigned assignRoom(ReceptionTask input, TaskContext task) {
        var found = reservation(input, task);
        if (found.isEmpty()) {
            return new RoomAssigned(Outcome.WAIT.name(), null, null);
        }
        var r = found.get();
        var hotel = input.pmsHotelCode();
        var id = OperaReservations.id(r);
        TaskHandlers.tag("opera.hotel", hotel);
        TaskHandlers.tag("opera.reservation.id", id);
        var current = OperaFrontDesk.room(r).orElse(null);
        if (OperaFrontDesk.inHouse(r) || OperaFrontDesk.checkedOut(r)) {
            log.info("{}/{}: already {} in room {}, nothing to assign", hotel, id, OperaFrontDesk.status(r), current);
            return new RoomAssigned(Outcome.STALE.name(), id, current);
        }
        var wanted = blank(input.roomNumber()) ? null : input.roomNumber().trim();
        if (wanted == null && current != null) {
            log.info("{}/{}: no room chosen at the desk; Opera's {} stands", hotel, id, current);
            return new RoomAssigned(Outcome.STALE.name(), id, current);
        }
        if (wanted != null && wanted.equals(current)) {
            return new RoomAssigned(Outcome.STALE.name(), id, current);
        }
        try {
            if (wanted == null) {
                var suggested = desk.suggestedRooms(hotel, id);
                if (suggested.isEmpty()) {
                    return refused(input, task, id, ReceptionOperation.CHECK_IN,
                            new PmsRejectedException(400, null, "Opera has no room to suggest for it"), RoomAssigned.class);
                }
                wanted = suggested.getFirst();
            }
            desk.assignRoom(hotel, id, wanted);
            log.info("{}/{}: room {} assigned in Opera{}", hotel, id, wanted, current == null ? "" : " (was " + current + ")");
            TaskHandlers.tag("opera.room", wanted);
            TaskHandlers.tag("opera.action", "room-assigned");
            return new RoomAssigned(Outcome.DONE.name(), id, wanted);
        } catch (PmsRejectedException e) {
            return refused(input, task, id, ReceptionOperation.CHECK_IN, e, RoomAssigned.class);
        }
    }

    /** The check-in, in Opera. Already in house: nothing to write. */
    public CheckedIn checkIn(ReceptionTask input, TaskContext task) {
        var found = reservation(input, task);
        if (found.isEmpty()) {
            return new CheckedIn(Outcome.WAIT.name(), null);
        }
        var r = found.get();
        var hotel = input.pmsHotelCode();
        var id = OperaReservations.id(r);
        TaskHandlers.tag("opera.hotel", hotel);
        TaskHandlers.tag("opera.reservation.id", id);
        if (OperaFrontDesk.inHouse(r) || OperaFrontDesk.checkedOut(r)) {
            log.info("{}/{}: Opera has it {} already", hotel, id, OperaFrontDesk.status(r));
            TaskHandlers.tag("opera.action", "already-in-house");
            events.written(hotel, id, input.hotelCode(), input.locator(), task.workflowDefinitionId());
            return new CheckedIn(Outcome.STALE.name(), id);
        }
        try {
            var room = OperaFrontDesk.room(r).orElse(blank(input.roomNumber()) ? null : input.roomNumber());
            desk.checkIn(hotel, id, room);
            log.info("{}/{}: checked in in Opera, room {}", hotel, id, room);
            TaskHandlers.tag("opera.room", room);
            TaskHandlers.tag("opera.action", "checked-in");
            outcomes.done(hotel, id, input.stayId(), ReceptionOperation.CHECK_IN, "En casa en Opera", room, null);
            events.written(hotel, id, input.hotelCode(), input.locator(), task.workflowDefinitionId());
            return new CheckedIn(Outcome.DONE.name(), id);
        } catch (PmsRejectedException e) {
            return refused(input, task, id, ReceptionOperation.CHECK_IN, e, CheckedIn.class);
        }
    }

    /** The check-out, in Opera, with the integration's cashier. Already out: nothing to write. */
    public CheckedOut checkOut(ReceptionTask input, TaskContext task) {
        var found = reservation(input, task);
        if (found.isEmpty()) {
            return new CheckedOut(Outcome.WAIT.name(), null);
        }
        var r = found.get();
        var hotel = input.pmsHotelCode();
        var id = OperaReservations.id(r);
        TaskHandlers.tag("opera.hotel", hotel);
        TaskHandlers.tag("opera.reservation.id", id);
        if (OperaFrontDesk.checkedOut(r)) {
            log.info("{}/{}: Opera has it checked out already", hotel, id);
            TaskHandlers.tag("opera.action", "already-checked-out");
            events.written(hotel, id, input.hotelCode(), input.locator(), task.workflowDefinitionId());
            return new CheckedOut(Outcome.STALE.name(), id);
        }
        try {
            if (!OperaFrontDesk.inHouse(r)) {
                // Checked out at the desk, but never checked in in Opera (its check-in waits, or was
                // discarded): there is nothing Opera could check out.
                throw new PmsRejectedException(409, "NOT_IN_HOUSE",
                        "Opera does not have it in house (" + OperaFrontDesk.status(r) + "): check it in first");
            }
            desk.checkOut(hotel, id);
            log.info("{}/{}: checked out in Opera", hotel, id);
            TaskHandlers.tag("opera.action", "checked-out");
            events.written(hotel, id, input.hotelCode(), input.locator(), task.workflowDefinitionId());
            return new CheckedOut(Outcome.DONE.name(), id);
        } catch (PmsRejectedException e) {
            return refused(input, task, id, ReceptionOperation.CHECK_OUT, e, CheckedOut.class);
        }
    }

    /**
     * The invoice Opera made at the check-out, to the front office: the folio's figures from Opera's
     * folio history, and its document when Opera gives one. Opera not having it does not stop the
     * check-out — the front office then offers its own proforma, labelled as such.
     */
    public InvoiceFetched fetchInvoice(InvoiceTask input, TaskContext task) {
        var hotel = TaskHandlers.required(task, ProcessVariables.PMS_HOTEL_CODE, input.pmsHotelCode());
        var id = TaskHandlers.required(task, ProcessVariables.PMS_RESERVATION_ID, input.pmsReservationId());
        var folios = desk.checkOutFolios(hotel, id);
        var folio = folios.isEmpty() ? null : folios.getFirst();
        String pdf = null;
        try {
            var document = desk.folioDocument(hotel, id, folio == null ? 1 : folio.window());
            if (document.isPresent() && document.get().length <= MAX_PDF_BYTES) {
                pdf = Base64.getEncoder().encodeToString(document.get());
            } else if (document.isPresent()) {
                log.warn("{}/{}: the folio document is {} bytes, too big to send; the figures go alone", hotel, id,
                        document.get().length);
            }
        } catch (PmsRejectedException e) {
            log.warn("{}/{}: Opera gave no folio document: {} ({})", hotel, id, e.getMessage(), e.errorCode());
            TaskHandlers.tag("opera.invoice.refused", e.getMessage());
        }
        if (folio == null && pdf == null) {
            log.info("{}/{}: Opera closed no folio at the check-out; the front office offers its proforma", hotel, id);
            outcomes.done(hotel, id, input.stayId(), ReceptionOperation.CHECK_OUT, "Salida registrada en Opera; sin folio",
                    null, null);
            return new InvoiceFetched(Outcome.SKIP.name());
        }
        var invoice = new Invoice("OPERA", folio == null ? null : folio.number(), folio == null ? null : folio.date(),
                folio == null ? null : folio.amount(), folio == null ? null : folio.currency(), pdf);
        TaskHandlers.tag("opera.invoice", invoice.number());
        outcomes.done(hotel, id, input.stayId(), ReceptionOperation.CHECK_OUT, "Salida registrada en Opera", null, invoice);
        return new InvoiceFetched(pdf != null ? Outcome.DONE.name() : "FIGURES");
    }

    /**
     * Nobody came: a comment on Opera's reservation says so — who reported it and when. Opera's own
     * «No Show» status is its Night Audit's to set. A reservation Opera has in the house is no no-show.
     */
    public NoShowRecorded recordNoShow(ReceptionTask input, TaskContext task) {
        var found = reservation(input, task);
        if (found.isEmpty()) {
            return new NoShowRecorded(Outcome.WAIT.name(), null);
        }
        var r = found.get();
        var hotel = input.pmsHotelCode();
        var id = OperaReservations.id(r);
        TaskHandlers.tag("opera.hotel", hotel);
        TaskHandlers.tag("opera.reservation.id", id);
        if (OperaFrontDesk.cancelled(r) || OperaFrontDesk.hasNoShowComment(r)) {
            log.info("{}/{}: Opera has it {} already", hotel, id, OperaFrontDesk.cancelled(r) ? OperaFrontDesk.status(r) : "as a no-show");
            TaskHandlers.tag("opera.action", "no-show-kept");
            return new NoShowRecorded(Outcome.STALE.name(), id);
        }
        try {
            if (OperaFrontDesk.inHouse(r) || OperaFrontDesk.checkedOut(r)) {
                throw new PmsRejectedException(409, "IN_HOUSE",
                        "Opera has the guests " + (OperaFrontDesk.inHouse(r) ? "in house" : "checked out") + ": it is no no-show");
            }
            desk.comment(hotel, id, "Front office", "%s — reported by the front office of %s (stay %s) at %s UTC; the CRS applies its fee"
                    .formatted(OperaFrontDesk.NO_SHOW_COMMENT, input.hotelCode(), input.stayId(),
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(clock.instant().atOffset(ZoneOffset.UTC))));
            log.info("{}/{}: the no-show recorded in Opera", hotel, id);
            TaskHandlers.tag("opera.action", "no-show-recorded");
            return new NoShowRecorded(Outcome.DONE.name(), id);
        } catch (PmsRejectedException e) {
            return refused(input, task, id, ReceptionOperation.NO_SHOW, e, NoShowRecorded.class);
        }
    }

    /**
     * Opera said no: the process waits on the refusal — a cause with its inbox notice, which a person
     * resolves once it is fixed in Opera, and then the process is relaunched — and the front office is
     * told why.
     */
    <T> T refused(ReceptionTask input, TaskContext task, String reservationId, ReceptionOperation operation,
                  PmsRejectedException e, Class<T> output) {
        log.warn("Opera refused {} of {}/{} ({}): {} ({})", task.stepId(), input.pmsHotelCode(), reservationId,
                input.stayId(), e.getMessage(), e.errorCode());
        TaskHandlers.tag("opera.refused", e.getMessage());
        await(input, List.of(Cause.pmsRejectedReservation(input.hotelCode(), input.locator(), task.stepId(),
                "%s — %s".formatted(e.getMessage(), e.errorCode()))));
        outcomes.refused(input.pmsHotelCode(), reservationId, input.stayId(), operation, e.getMessage());
        var wait = Outcome.WAIT.name();
        if (output == RoomAssigned.class) {
            return output.cast(new RoomAssigned(wait, reservationId, null));
        }
        if (output == CheckedIn.class) {
            return output.cast(new CheckedIn(wait, reservationId));
        }
        if (output == CheckedOut.class) {
            return output.cast(new CheckedOut(wait, reservationId));
        }
        return output.cast(new NoShowRecorded(wait, reservationId));
    }

    void await(ReceptionTask input, List<Cause> causes) {
        TaskHandlers.tag("mapping.causes", String.join("; ", causes.stream().map(c -> c.key() + "=" + c.description()).toList()));
        integration.await(input.processKey(), input.definitionId(), input.hotelCode(), input.locator(), input.variables(), causes);
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static void add(List<Variable> variables, String name, String value) {
        if (value != null) {
            variables.add(new Variable(name, value));
        }
    }
}
