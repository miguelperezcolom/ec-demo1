package io.mateu.ecdemo1.pmsintegration.worker;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.process.Outcome;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.config.PaymentMethods;
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
 * The desk's payments, onto Opera's folio (pms-fo): the steps of «registrar-cobro» ({@code post-payment})
 * and «devolver-cobro» ({@code refund-payment}). The PMS is the master of the folio: what the front
 * office's till takes during the stay — a payment, an advance — is posted to the reservation's folio in
 * Opera, so Opera's balance is what is still owed and its check-out settles only that.
 *
 * <p>Idempotent by the front office's payment: the posting carries {@code FO:PAY:<paymentId>} as its
 * reference ({@code …:R} its refund), and each step looks for it before it writes. A refund is the same
 * payment, negative, naming the original's transaction number.
 *
 * <p>Order and waits as the charges' ({@link ChargeHandlers}): the reservation's lock; a payment for a
 * reservation Opera does not have in the house yet waits on a cause its check-in resolves; a refund
 * whose payment Opera does not have yet waits on one the payment's posting resolves. The front office
 * hears how it went as a {@code RecordCharge} on the line {@code PAY:<paymentId>}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentHandlers {

    static final String POST_PAYMENT = "post-payment";
    static final String REFUND_PAYMENT = "refund-payment";

    final OperaFrontDesk desk;
    final OperaReservations reservations;
    final IntegrationClients integration;
    final ReceptionOutcomes outcomes;
    final PaymentMethods methods;

    /** The input of both steps: the stay both ways, and the payment — its id, kind, method and amount. */
    public record PaymentTask(String definitionId, String processKey, String hotelCode, String locator, String pmsHotelCode,
                              String pmsReservationId, String stayId, String eventId, String origin, String paymentId,
                              String paymentKind, String paymentMethod, String paymentReference, String amount,
                              String currency) {

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
            ReceptionHandlers.add(variables, ProcessVariables.PAYMENT_ID, paymentId);
            ReceptionHandlers.add(variables, ProcessVariables.PAYMENT_KIND, paymentKind);
            ReceptionHandlers.add(variables, ProcessVariables.PAYMENT_METHOD, paymentMethod);
            ReceptionHandlers.add(variables, ProcessVariables.PAYMENT_REFERENCE, paymentReference);
            ReceptionHandlers.add(variables, ProcessVariables.AMOUNT, amount);
            ReceptionHandlers.add(variables, ProcessVariables.CURRENCY, currency);
            return variables;
        }

        boolean fromTheCrs() {
            return !pmsHotelCode.equals(hotelCode);
        }

        /** The front office's line the answer names: how its till tells payments from charges. */
        String line() {
            return "PAY:" + paymentId;
        }

        String reference() {
            return "FO:PAY:" + paymentId;
        }

        String refundReference() {
            return reference() + ":R";
        }

        String remark() {
            return ("DEPOSIT".equals(paymentKind) ? "Anticipo" : "Cobro") + " front office"
                    + (paymentReference == null || paymentReference.isBlank() ? "" : " · " + paymentReference);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentPosted(String paymentOutcome, String pmsReservationId, String pmsPostingId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentRefunded(String refundOutcome, String pmsReservationId, String pmsRefundId) {
    }

    /** The payment, on Opera's folio — unless it is there already. */
    public PaymentPosted postPayment(PaymentTask input, TaskContext task) {
        var amount = amount(input, task);
        var found = reservation(input, task);
        if (found.isEmpty()) {
            return new PaymentPosted(Outcome.WAIT.name(), null, null);
        }
        var r = found.get();
        var hotel = input.pmsHotelCode();
        var id = OperaReservations.id(r);
        tags(input, hotel, id);
        if (OperaFrontDesk.cancelled(r)) {
            log.info("{}/{}: Opera has it {}: payment {} not posted", hotel, id, OperaFrontDesk.status(r), input.paymentId());
            outcomes.charge(hotel, id, input.stayId(), input.line(), false, true,
                    "Opera la tiene cancelada (" + OperaFrontDesk.status(r) + "): el cobro no va a su folio", null);
            return new PaymentPosted(Outcome.STALE.name(), id, null);
        }
        var existing = desk.posting(hotel, id, input.reference());
        if (existing.isPresent()) {
            log.info("{}/{}: payment {} is on Opera's folio already ({})", hotel, id, input.paymentId(),
                    existing.get().transactionNo());
            TaskHandlers.tag("opera.action", "payment-already-posted");
            posted(input, id, existing.get().transactionNo());
            return new PaymentPosted(Outcome.STALE.name(), id, existing.get().transactionNo());
        }
        try {
            ChargeHandlers.inHouse(r);
            var method = methods.of(input.paymentMethod());
            desk.postPayment(hotel, id, method, amount, input.currency(), input.reference(), input.remark(), null);
            var posting = desk.posting(hotel, id, input.reference()).map(OperaFrontDesk.Posting::transactionNo).orElse(null);
            log.info("{}/{}: payment {} of {} posted to Opera's folio with {} ({})", hotel, id, input.paymentId(), amount,
                    method, posting);
            TaskHandlers.tag("opera.action", "payment-posted");
            TaskHandlers.tag("opera.payment.method", method);
            TaskHandlers.tag("opera.posting", posting);
            posted(input, id, posting);
            return new PaymentPosted(Outcome.DONE.name(), id, posting);
        } catch (PmsRejectedException e) {
            refused(input, task, id, false, e);
            return new PaymentPosted(Outcome.WAIT.name(), id, null);
        }
    }

    /** The payment's refund, on Opera's folio — unless it is there already. */
    public PaymentRefunded refundPayment(PaymentTask input, TaskContext task) {
        var amount = amount(input, task);
        var found = reservation(input, task);
        if (found.isEmpty()) {
            return new PaymentRefunded(Outcome.WAIT.name(), null, null);
        }
        var r = found.get();
        var hotel = input.pmsHotelCode();
        var id = OperaReservations.id(r);
        tags(input, hotel, id);
        if (OperaFrontDesk.cancelled(r)) {
            log.info("{}/{}: Opera has it {}: nothing to refund for {}", hotel, id, OperaFrontDesk.status(r), input.paymentId());
            return new PaymentRefunded(Outcome.STALE.name(), id, null);
        }
        var postings = desk.postings(hotel, id);
        var refund = postings.stream().filter(p -> input.refundReference().equals(p.reference())).findFirst();
        if (refund.isPresent()) {
            log.info("{}/{}: payment {} is refunded on Opera's folio already ({})", hotel, id, input.paymentId(),
                    refund.get().transactionNo());
            TaskHandlers.tag("opera.action", "payment-already-refunded");
            outcomes.charge(hotel, id, input.stayId(), input.line(), true, false, "Devuelto en el folio de Opera",
                    refund.get().transactionNo());
            return new PaymentRefunded(Outcome.STALE.name(), id, refund.get().transactionNo());
        }
        var original = postings.stream().filter(p -> input.reference().equals(p.reference())).findFirst();
        try {
            if (original.isEmpty()) {
                // Opera does not have the payment yet (its posting waits, or was refused): nothing to give back
                // — yet. The payment's posting resolves this when it gets there.
                throw new PmsRejectedException(409, "NOT_POSTED",
                        "the payment " + input.paymentId() + " is not on Opera's folio yet: nothing to refund");
            }
            ChargeHandlers.inHouse(r);
            var method = methods.of(input.paymentMethod());
            desk.postPayment(hotel, id, method, amount.abs().negate(), input.currency(), input.refundReference(),
                    "Devuelto: " + input.remark(), original.get().transactionNo());
            var posting = desk.posting(hotel, id, input.refundReference()).map(OperaFrontDesk.Posting::transactionNo)
                    .orElse(null);
            log.info("{}/{}: payment {} ({}) refunded on Opera's folio ({})", hotel, id, input.paymentId(),
                    original.get().transactionNo(), posting);
            TaskHandlers.tag("opera.action", "payment-refunded");
            TaskHandlers.tag("opera.posting", posting);
            outcomes.charge(hotel, id, input.stayId(), input.line(), true, false, "Devuelto en el folio de Opera", posting);
            return new PaymentRefunded(Outcome.DONE.name(), id, posting);
        } catch (PmsRejectedException e) {
            refused(input, task, id, true, e);
            return new PaymentRefunded(Outcome.WAIT.name(), id, null);
        }
    }

    /** The payment got onto Opera's folio: the front office is told, and a refund that waited for it can go. */
    void posted(PaymentTask input, String reservationId, String posting) {
        outcomes.charge(input.pmsHotelCode(), reservationId, input.stayId(), input.line(), false, false,
                "En el folio de Opera", posting);
        resolve(input, REFUND_PAYMENT, "payment " + input.paymentId() + " posted to Opera's folio");
        resolve(input, POST_PAYMENT, "payment " + input.paymentId() + " posted to Opera's folio");
    }

    void resolve(PaymentTask input, String step, String why) {
        try {
            integration.resolveCauseIfOpen(Cause.pmsRejectedReservation(input.hotelCode(), input.locator(), step, "").key(),
                    "pms-integration: " + why);
        } catch (RuntimeException e) {
            log.warn("{}: a wait of its {} could not be resolved: {}", input.locator(), step, e.getMessage());
        }
    }

    void refused(PaymentTask input, TaskContext task, String reservationId, boolean refund, PmsRejectedException e) {
        log.warn("Opera refused {} of {}/{} (payment {} of stay {}): {} ({})", task.stepId(), input.pmsHotelCode(),
                reservationId, input.paymentId(), input.stayId(), e.getMessage(), e.errorCode());
        TaskHandlers.tag("opera.refused", e.getMessage());
        await(input, List.of(Cause.pmsRejectedReservation(input.hotelCode(), input.locator(), task.stepId(), e.reason())));
        outcomes.charge(input.pmsHotelCode(), reservationId, input.stayId(), input.line(), refund, true, e.getMessage(), null);
    }

    Optional<JsonNode> reservation(PaymentTask input, TaskContext task) {
        TaskHandlers.required(task, ProcessVariables.PROCESS_KEY, input.processKey());
        TaskHandlers.required(task, ProcessVariables.DEFINITION_ID, input.definitionId());
        var hotel = TaskHandlers.required(task, ProcessVariables.PMS_HOTEL_CODE, input.pmsHotelCode());
        TaskHandlers.required(task, ProcessVariables.HOTEL_CODE, input.hotelCode());
        TaskHandlers.required(task, ProcessVariables.LOCATOR, input.locator());
        TaskHandlers.required(task, ProcessVariables.PAYMENT_ID, input.paymentId());
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

    static BigDecimal amount(PaymentTask input, TaskContext task) {
        var amount = TaskHandlers.required(task, ProcessVariables.AMOUNT, input.amount());
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Step %s: the amount «%s» is not a number".formatted(task.stepId(), amount));
        }
    }

    static void tags(PaymentTask input, String hotel, String id) {
        TaskHandlers.tag("opera.hotel", hotel);
        TaskHandlers.tag("opera.reservation.id", id);
        TaskHandlers.tag("frontoffice.payment", input.paymentId());
    }

    void await(PaymentTask input, List<Cause> causes) {
        TaskHandlers.tag("mapping.causes", String.join("; ", causes.stream().map(c -> c.key() + "=" + c.description()).toList()));
        integration.await(input.processKey(), input.definitionId(), input.hotelCode(), input.locator(), input.variables(), causes);
    }
}
