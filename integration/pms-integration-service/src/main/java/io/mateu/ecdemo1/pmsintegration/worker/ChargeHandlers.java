package io.mateu.ecdemo1.pmsintegration.worker;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.process.Outcome;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.config.ChargeCodes;
import io.mateu.ecdemo1.pmsintegration.frontoffice.ReceptionOutcomes;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaFrontDesk;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaReservations;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsRejectedException;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.worker.api.TaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The desk's charges, onto Opera's folio (pms-fo): the steps of «registrar-cargo» ({@code post-charge})
 * and «anular-cargo» ({@code reverse-charge}). The PMS is the master of the folio: a charge the front
 * office puts on the stay's folio — a late check-out, an extra, a consumption — is posted to the
 * reservation's folio in Opera, so that what Opera settles and invoices at the check-out covers it.
 *
 * <p>Idempotent by the front office's folio line: the posting carries {@code FO:<lineId>} as its
 * reference ({@code FO:<lineId>:R} its reversal), and each step looks for it before it writes — a line
 * posted already is not posted again (STALE), and the step answers with Opera's transaction number. A
 * reversal is the same charge, negative, with the same transaction code.
 *
 * <p>Order: both steps hold the reservation's lock, so they follow the check-in and precede the
 * check-out the desk did after them. A charge for a reservation Opera does not have in the house yet —
 * its check-in waits on a refusal — waits on a cause that the check-in resolves when it gets in; a void
 * whose charge Opera does not have yet waits on one that the charge's posting resolves. A refusal of
 * Opera is a cause the process waits on, and the front office is told why.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChargeHandlers {

    static final String POST_CHARGE = "post-charge";
    static final String REVERSE_CHARGE = "reverse-charge";

    final OperaFrontDesk desk;
    final OperaReservations reservations;
    final IntegrationClients integration;
    final ReceptionOutcomes outcomes;
    final ChargeCodes codes;

    /**
     * The input of both steps: the stay both ways (as the reception's steps), and the folio line — its
     * id, what it is, its concept and amount.
     */
    public record ChargeTask(String definitionId, String processKey, String hotelCode, String locator, String pmsHotelCode,
                             String pmsReservationId, String stayId, String eventId, String origin, String lineId,
                             String chargeKind, String chargeCode, String description, String amount, String currency) {

        List<Variable> variables() {
            var variables = new ArrayList<Variable>();
            ReceptionHandlers.add(variables, ProcessVariables.DEFINITION_ID, definitionId);
            ReceptionHandlers.add(variables, ProcessVariables.PROCESS_KEY, processKey);
            ReceptionHandlers.add(variables, ProcessVariables.HOTEL_CODE, hotelCode);
            ReceptionHandlers.add(variables, ProcessVariables.LOCATOR, locator);
            ReceptionHandlers.add(variables, ProcessVariables.PMS_HOTEL_CODE, pmsHotelCode);
            ReceptionHandlers.add(variables, ProcessVariables.PMS_RESERVATION_ID, pmsReservationId);
            ReceptionHandlers.add(variables, ProcessVariables.STAY_ID, stayId);
            ReceptionHandlers.add(variables, ProcessVariables.EVENT_ID, eventId);
            ReceptionHandlers.add(variables, ProcessVariables.ORIGIN, origin);
            ReceptionHandlers.add(variables, ProcessVariables.LINE_ID, lineId);
            ReceptionHandlers.add(variables, ProcessVariables.CHARGE_KIND, chargeKind);
            ReceptionHandlers.add(variables, ProcessVariables.CHARGE_CODE, chargeCode);
            ReceptionHandlers.add(variables, ProcessVariables.DESCRIPTION, description);
            ReceptionHandlers.add(variables, ProcessVariables.AMOUNT, amount);
            ReceptionHandlers.add(variables, ProcessVariables.CURRENCY, currency);
            return variables;
        }

        boolean fromTheCrs() {
            return !pmsHotelCode.equals(hotelCode);
        }

        /** The reference the charge's posting carries in Opera: how it is found again. */
        String reference() {
            return reference(lineId);
        }

        /** The reference its reversal carries. */
        String reversalReference() {
            return reference(lineId) + ":R";
        }

        static String reference(String lineId) {
            return "FO:" + lineId;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChargePosted(String chargeOutcome, String pmsReservationId, String pmsPostingId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChargeReversed(String reversalOutcome, String pmsReservationId, String pmsReversalId) {
    }

    /** The charge, on Opera's folio — unless it is there already. */
    public ChargePosted postCharge(ChargeTask input, TaskContext task) {
        var amount = amount(input, task);
        var found = reservation(input, task);
        if (found.isEmpty()) {
            return new ChargePosted(Outcome.WAIT.name(), null, null);
        }
        var r = found.get();
        var hotel = input.pmsHotelCode();
        var id = OperaReservations.id(r);
        tags(input, hotel, id);
        if (OperaFrontDesk.cancelled(r)) {
            log.info("{}/{}: Opera has it {}: charge {} not posted", hotel, id, OperaFrontDesk.status(r), input.lineId());
            outcomes.charge(hotel, id, input.stayId(), input.lineId(), false, true,
                    "Opera la tiene cancelada (" + OperaFrontDesk.status(r) + "): el cargo no va a su folio", null);
            return new ChargePosted(Outcome.STALE.name(), id, null);
        }
        var existing = desk.posting(hotel, id, input.reference());
        if (existing.isPresent()) {
            log.info("{}/{}: charge {} is on Opera's folio already ({})", hotel, id, input.lineId(), existing.get().transactionNo());
            TaskHandlers.tag("opera.action", "charge-already-posted");
            posted(input, id, existing.get().transactionNo());
            return new ChargePosted(Outcome.STALE.name(), id, existing.get().transactionNo());
        }
        try {
            inHouse(r);
            var code = codes.of(input.chargeKind(), input.chargeCode());
            desk.postCharge(hotel, id, code, amount, input.currency(), input.reference(), input.description());
            var posting = desk.posting(hotel, id, input.reference()).map(OperaFrontDesk.Posting::transactionNo).orElse(null);
            log.info("{}/{}: charge {} «{}» {} posted to Opera's folio with {} ({})", hotel, id, input.lineId(),
                    input.description(), amount, code, posting);
            TaskHandlers.tag("opera.action", "charge-posted");
            TaskHandlers.tag("opera.transaction.code", code);
            TaskHandlers.tag("opera.posting", posting);
            posted(input, id, posting);
            return new ChargePosted(Outcome.DONE.name(), id, posting);
        } catch (PmsRejectedException e) {
            refused(input, task, id, false, e);
            return new ChargePosted(Outcome.WAIT.name(), id, null);
        }
    }

    /** The charge's reversal, on Opera's folio — unless it is there already. */
    public ChargeReversed reverseCharge(ChargeTask input, TaskContext task) {
        var amount = amount(input, task);
        var found = reservation(input, task);
        if (found.isEmpty()) {
            return new ChargeReversed(Outcome.WAIT.name(), null, null);
        }
        var r = found.get();
        var hotel = input.pmsHotelCode();
        var id = OperaReservations.id(r);
        tags(input, hotel, id);
        if (OperaFrontDesk.cancelled(r)) {
            log.info("{}/{}: Opera has it {}: nothing to reverse for {}", hotel, id, OperaFrontDesk.status(r), input.lineId());
            return new ChargeReversed(Outcome.STALE.name(), id, null);
        }
        var postings = desk.postings(hotel, id);
        var reversal = postings.stream().filter(p -> input.reversalReference().equals(p.reference())).findFirst();
        if (reversal.isPresent()) {
            log.info("{}/{}: charge {} is reversed on Opera's folio already ({})", hotel, id, input.lineId(),
                    reversal.get().transactionNo());
            TaskHandlers.tag("opera.action", "charge-already-reversed");
            outcomes.charge(hotel, id, input.stayId(), input.lineId(), true, false, "Anulado en el folio de Opera",
                    reversal.get().transactionNo());
            return new ChargeReversed(Outcome.STALE.name(), id, reversal.get().transactionNo());
        }
        var original = postings.stream().filter(p -> input.reference().equals(p.reference())).findFirst();
        try {
            if (original.isEmpty()) {
                // Opera does not have the charge yet (its posting waits, or was refused): there is nothing to
                // reverse — yet. The charge's posting resolves this when it gets there.
                throw new PmsRejectedException(409, "NOT_POSTED",
                        "the charge " + input.lineId() + " is not on Opera's folio yet: nothing to reverse");
            }
            inHouse(r);
            var code = original.get().transactionCode() != null ? original.get().transactionCode()
                    : codes.of(input.chargeKind(), input.chargeCode());
            var back = (original.get().amount() != null ? original.get().amount() : amount).abs().negate();
            desk.postCharge(hotel, id, code, back, input.currency(), input.reversalReference(),
                    "Anulado: " + (input.description() == null ? "" : input.description()));
            var posting = desk.posting(hotel, id, input.reversalReference()).map(OperaFrontDesk.Posting::transactionNo)
                    .orElse(null);
            log.info("{}/{}: charge {} ({}) reversed on Opera's folio: {} with {} ({})", hotel, id, input.lineId(),
                    original.get().transactionNo(), back, code, posting);
            TaskHandlers.tag("opera.action", "charge-reversed");
            TaskHandlers.tag("opera.posting", posting);
            outcomes.charge(hotel, id, input.stayId(), input.lineId(), true, false, "Anulado en el folio de Opera", posting);
            return new ChargeReversed(Outcome.DONE.name(), id, posting);
        } catch (PmsRejectedException e) {
            refused(input, task, id, true, e);
            return new ChargeReversed(Outcome.WAIT.name(), id, null);
        }
    }

    /** Opera takes charges on the folio of a reservation in the house only. */
    static void inHouse(JsonNode reservation) {
        if (OperaFrontDesk.checkedOut(reservation)) {
            throw new PmsRejectedException(409, "CHECKED_OUT",
                    "Opera has checked it out already: its folio is closed, the charge cannot go on it");
        }
        if (!OperaFrontDesk.inHouse(reservation)) {
            throw new PmsRejectedException(409, "NOT_IN_HOUSE",
                    "Opera does not have it in house (" + OperaFrontDesk.status(reservation) + "): its check-in comes first");
        }
    }

    /**
     * The charge got onto Opera's folio: the front office is told its transaction number, and a void of
     * it that waited for it has it to reverse now.
     */
    void posted(ChargeTask input, String reservationId, String posting) {
        outcomes.charge(input.pmsHotelCode(), reservationId, input.stayId(), input.lineId(), false, false,
                "En el folio de Opera", posting);
        resolve(input, REVERSE_CHARGE, "charge " + input.lineId() + " posted to Opera's folio");
        resolve(input, POST_CHARGE, "charge " + input.lineId() + " posted to Opera's folio");
    }

    void resolve(ChargeTask input, String step, String why) {
        try {
            integration.resolveCauseIfOpen(Cause.pmsRejectedReservation(input.hotelCode(), input.locator(), step, "").key(),
                    "pms-integration: " + why);
        } catch (RuntimeException e) {
            log.warn("{}: a wait of its {} could not be resolved: {}", input.locator(), step, e.getMessage());
        }
    }

    void refused(ChargeTask input, TaskContext task, String reservationId, boolean reversal, PmsRejectedException e) {
        log.warn("Opera refused {} of {}/{} (line {} of stay {}): {} ({})", task.stepId(), input.pmsHotelCode(), reservationId,
                input.lineId(), input.stayId(), e.getMessage(), e.errorCode());
        TaskHandlers.tag("opera.refused", e.getMessage());
        await(input, List.of(Cause.pmsRejectedReservation(input.hotelCode(), input.locator(), task.stepId(),
                e.reason())));
        outcomes.charge(input.pmsHotelCode(), reservationId, input.stayId(), input.lineId(), reversal, true, e.getMessage(), null);
    }

    /** The Opera reservation of the step, or empty having registered the wait for it to reach Opera. */
    Optional<JsonNode> reservation(ChargeTask input, TaskContext task) {
        TaskHandlers.required(task, ProcessVariables.PROCESS_KEY, input.processKey());
        TaskHandlers.required(task, ProcessVariables.DEFINITION_ID, input.definitionId());
        var hotel = TaskHandlers.required(task, ProcessVariables.PMS_HOTEL_CODE, input.pmsHotelCode());
        TaskHandlers.required(task, ProcessVariables.HOTEL_CODE, input.hotelCode());
        TaskHandlers.required(task, ProcessVariables.LOCATOR, input.locator());
        TaskHandlers.required(task, ProcessVariables.LINE_ID, input.lineId());
        Optional<JsonNode> found;
        if (input.pmsReservationId() != null && !input.pmsReservationId().isBlank()) {
            found = desk.reservation(hotel, input.pmsReservationId());
        } else if (input.fromTheCrs()) {
            found = reservations.byLocator(hotel, input.locator());
        } else {
            found = desk.reservation(hotel, input.locator());
        }
        if (found.isEmpty()) {
            await(input, List.of(Cause.notYetProjected(input.hotelCode(), input.locator())));
        }
        return found;
    }

    static BigDecimal amount(ChargeTask input, TaskContext task) {
        var amount = TaskHandlers.required(task, ProcessVariables.AMOUNT, input.amount());
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Step %s: the amount «%s» is not a number".formatted(task.stepId(), amount));
        }
    }

    static void tags(ChargeTask input, String hotel, String id) {
        TaskHandlers.tag("opera.hotel", hotel);
        TaskHandlers.tag("opera.reservation.id", id);
        TaskHandlers.tag("frontoffice.folio.line", input.lineId());
    }

    void await(ChargeTask input, List<Cause> causes) {
        TaskHandlers.tag("mapping.causes", String.join("; ", causes.stream().map(c -> c.key() + "=" + c.description()).toList()));
        integration.await(input.processKey(), input.definitionId(), input.hotelCode(), input.locator(), input.variables(), causes);
    }
}
