package io.mateu.ecdemo1.pmsintegration.worker;

import io.mateu.ecdemo1.integration.model.customer.IdentityRequest;
import io.mateu.ecdemo1.integration.model.customer.ResolvedIdentity;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.PmsIntegrationProperties;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.process.Outcome;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients.CodeRef;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsTransientException;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaProfiles;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaReservations;
import io.mateu.ecdemo1.pmsintegration.ohip.PmsRejectedException;
import io.mateu.ecdemo1.pmsintegration.write.ReservationPayload;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The steps that write to Opera, by step id. Each reads what it acts on again — the process carries
 * references only — and each is idempotent: it looks before it creates, and it writes state, not
 * increments, so running it twice leaves Opera as running it once.
 *
 * <p>Failures follow the HLA's policy. A transient one propagates, and the engine retries the step
 * with backoff, for as long as it takes. A refusal becomes a cause the process waits on; so does a
 * code that stopped having an equivalence since the reservation was prepared.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskHandlers {

    final IntegrationClients integration;
    final OperaProfiles profiles;
    final OperaReservations reservations;
    final ReservationPayload payload;
    final Connections connections;
    final ReservationLocks locks;
    final PmsIntegrationProperties settings;
    final OhipProperties ohipProperties;

    public Map<String, Function<TaskExecutionRequested, List<Variable>>> handlers() {
        return Map.of(
                "ensure-guest-profile", this::ensureGuestProfile,
                "upsert-reservation", task -> locked(task, this::upsertReservation),
                "cancel-reservation", task -> locked(task, this::cancelReservation),
                "ensure-partner-profile", this::ensurePartnerProfile);
    }

    List<Variable> ensureGuestProfile(TaskExecutionRequested task) {
        var r = reservation(task);
        var hotel = resolveHotel(task, r);
        if (hotel == null) {
            return outcome(ProcessVariables.PROFILE_OUTCOME, Outcome.WAIT);
        }
        var customerId = holderCustomer(r);
        try {
            var known = ohipProperties.profileReferences() ? null
                    : reservations.byLocator(hotel, r.locator()).flatMap(OperaReservations::guestProfileId).orElse(null);
            var ensured = profiles.ensureGuest(hotel, r.locator(), r.holder(), customerId, known);
            log.info("Guest profile {} {} for {} (customer {})", ensured.profileId(), ensured.created() ? "created" : "updated",
                    r.locator(), customerId);
            var variables = new ArrayList<>(List.of(new Variable(ProcessVariables.PROFILE_OUTCOME, Outcome.OK.name()),
                    new Variable(ProcessVariables.GUEST_PROFILE_ID, ensured.profileId())));
            if (customerId != null) {
                variables.add(new Variable(ProcessVariables.CUSTOMER_ID, customerId));
            }
            return variables;
        } catch (PmsRejectedException e) {
            return rejected(task, r, "guest profile of " + r.locator(), e, ProcessVariables.PROFILE_OUTCOME);
        }
    }

    /**
     * Who the passengers are, asked of the customer MDM — the holder first, then each room's guests —
     * and the holder's customer code. The MDM is not a gate (HLA CRM-MDM, «no bloquear la venta»):
     * if it does not answer, the profile goes without the code, and the next projection of the
     * reservation stamps it.
     */
    String holderCustomer(Reservation r) {
        var passengers = new ArrayList<Person>();
        if (r.holder() != null) {
            passengers.add(r.holder());
        }
        r.rooms().forEach(room -> {
            if (room.guests() != null) {
                passengers.addAll(room.guests());
            }
        });
        if (passengers.isEmpty()) {
            return null;
        }
        try {
            var identities = integration.identities(new IdentityRequest(r.hotelCode(), r.locator(), passengers));
            return identities.stream().filter(i -> i.passenger() == 0).map(ResolvedIdentity::customerId).findFirst().orElse(null);
        } catch (RuntimeException e) {
            log.warn("Customer MDM unavailable for {}: the guest profile goes without its customer code ({})", r.locator(), e.getMessage());
            return null;
        }
    }

    /**
     * The HLA's «Grabar la reserva»: find it by the CRS locator, compare the CRS version its UDF
     * carries with the one being written, and create or update — or, if Opera already holds this
     * version or a newer one, write nothing. The read-compare-write is serialised per reservation
     * ({@link ReservationLocks}): OHIP has no conditional write to make it atomic.
     */
    List<Variable> upsertReservation(TaskExecutionRequested task) {
        var r = reservation(task);
        var codes = new LinkedHashSet<CodeRef>();
        codes.add(new CodeRef(CodeType.HOTEL, r.hotelCode()));
        codes.add(new CodeRef(CodeType.CHANNEL, r.channelCode()));
        r.rooms().forEach(room -> {
            codes.add(new CodeRef(CodeType.ROOM_TYPE, room.roomTypeCode()));
            codes.add(new CodeRef(CodeType.RATE_PLAN, room.ratePlanCode()));
            codes.add(new CodeRef(CodeType.BOARD, room.boardCode()));
        });
        r.payments().forEach(p -> codes.add(new CodeRef(CodeType.PAYMENT_METHOD, p.methodCode())));
        var resolved = integration.resolve(r.hotelCode(), new ArrayList<>(codes));
        var partner = r.partnerCode() == null ? null : integration.partner(r.partnerCode());
        var partnerProfile = r.partnerCode() == null ? null : integration.partnerProfile(r.partnerCode()).orElse(null);
        var missing = new ArrayList<>(resolved.missing());
        if (partner != null && partnerProfile == null) {
            missing.add(Cause.missingPartner(partner.code()));
        }
        if (!missing.isEmpty()) {
            // Approved when the reservation was prepared, gone since: wait again, as preparing would.
            await(task, r, missing);
            return outcome(ProcessVariables.WRITE_OUTCOME, Outcome.WAIT);
        }
        var hotel = resolved.target(CodeType.HOTEL, r.hotelCode());
        try {
            var body = payload.build(r, resolved, hotel, var(task, ProcessVariables.GUEST_PROFILE_ID), partner,
                    partnerProfile == null ? null : partnerProfile.pmsProfileId(),
                    partnerProfile == null ? null : partnerProfile.profileType());
            var existing = reservations.byLocator(hotel, r.locator());
            String reservationId;
            if (existing.isEmpty()) {
                reservationId = reservations.create(hotel, body);
                log.info("{} v{} created in {} as {}", r.locator(), r.version(), hotel, reservationId);
            } else {
                reservationId = OperaReservations.id(existing.get());
                var written = reservations.writtenVersion(existing.get());
                if (written >= r.version() || OperaReservations.cancelled(existing.get())) {
                    log.info("{} v{}: Opera already holds v{}{}, nothing to write", r.locator(), r.version(), written,
                            OperaReservations.cancelled(existing.get()) ? " (cancelled)" : "");
                    return List.of(new Variable(ProcessVariables.WRITE_OUTCOME, Outcome.STALE.name()),
                            new Variable(ProcessVariables.PMS_RESERVATION_ID, reservationId));
                }
                reservations.update(hotel, reservationId, body);
                log.info("{} v{} -> v{} updated in {} ({})", r.locator(), written, r.version(), hotel, reservationId);
            }
            if (!ohipProperties.postDeposits() && !r.payments().isEmpty()) {
                log.info("{}: {} payment(s) not posted to the folio — deposits are off for this tenant", r.locator(), r.payments().size());
            }
            for (var payment : ohipProperties.postDeposits() ? r.payments() : List.<io.mateu.ecdemo1.integration.model.reservation.Payment>of()) {
                reservations.ensureDeposit(hotel, reservationId, payment,
                        resolved.target(CodeType.PAYMENT_METHOD, payment.methodCode()), r.currency());
            }
            return List.of(new Variable(ProcessVariables.WRITE_OUTCOME, Outcome.DONE.name()),
                    new Variable(ProcessVariables.PMS_RESERVATION_ID, reservationId));
        } catch (PmsRejectedException e) {
            return rejected(task, r, "reservation " + r.locator(), e, ProcessVariables.WRITE_OUTCOME);
        }
    }

    /**
     * Cancels in Opera. A reservation Opera does not have yet is not skipped: the cancellation waits
     * for it to be projected, so the PMS keeps the record and a penalty would have a folio (R37).
     */
    List<Variable> cancelReservation(TaskExecutionRequested task) {
        var r = reservation(task);
        var codes = new ArrayList<CodeRef>();
        codes.add(new CodeRef(CodeType.HOTEL, r.hotelCode()));
        if (r.cancellationReasonCode() != null) {
            codes.add(new CodeRef(CodeType.CANCELLATION_REASON, r.cancellationReasonCode()));
        }
        var resolved = integration.resolve(r.hotelCode(), codes);
        if (!resolved.missing().isEmpty()) {
            await(task, r, resolved.missing());
            return outcome(ProcessVariables.WRITE_OUTCOME, Outcome.WAIT);
        }
        var hotel = resolved.target(CodeType.HOTEL, r.hotelCode());
        var existing = reservations.byLocator(hotel, r.locator());
        if (existing.isEmpty()) {
            await(task, r, List.of(Cause.notYetProjected(r.hotelCode(), r.locator())));
            return outcome(ProcessVariables.WRITE_OUTCOME, Outcome.WAIT);
        }
        var reservationId = OperaReservations.id(existing.get());
        if (OperaReservations.cancelled(existing.get())) {
            return List.of(new Variable(ProcessVariables.WRITE_OUTCOME, Outcome.STALE.name()),
                    new Variable(ProcessVariables.PMS_RESERVATION_ID, reservationId));
        }
        try {
            var reason = r.cancellationReasonCode() == null ? null
                    : resolved.target(CodeType.CANCELLATION_REASON, r.cancellationReasonCode());
            reservations.cancel(hotel, reservationId, reason, r.cancellationReasonCode() == null
                    ? "Cancelled in the CRS" : "Cancelled in the CRS (reason " + r.cancellationReasonCode() + ")");
            log.info("{} cancelled in {} ({})", r.locator(), hotel, reservationId);
            return List.of(new Variable(ProcessVariables.WRITE_OUTCOME, Outcome.DONE.name()),
                    new Variable(ProcessVariables.PMS_RESERVATION_ID, reservationId));
        } catch (PmsRejectedException e) {
            return rejected(task, r, "cancellation of " + r.locator(), e, ProcessVariables.WRITE_OUTCOME);
        }
    }

    /**
     * A partner as a profile of the chain in Opera (F004, R12): once, not per hotel. A version the
     * profile already carries, or an older one, is not written again.
     */
    List<Variable> ensurePartnerProfile(TaskExecutionRequested task) {
        var partner = integration.partner(var(task, ProcessVariables.PARTNER_CODE));
        if (settings.partnersOwnedByPms()) {
            return resolvePartnerProfile(task, partner);
        }
        var resolved = integration.resolve(null, List.of(new CodeRef(CodeType.PARTNER_TYPE, partner.type().name())));
        if (!resolved.missing().isEmpty()) {
            integration.await(var(task, ProcessVariables.PROCESS_KEY), var(task, ProcessVariables.DEFINITION_ID), null,
                    partner.code(), task.variables(), resolved.missing());
            return outcome(ProcessVariables.PROFILE_OUTCOME, Outcome.WAIT);
        }
        var profileType = resolved.target(CodeType.PARTNER_TYPE, partner.type().name());
        var hotel = anyHotel();
        try {
            var current = profiles.byExternalId(hotel, "PARTNER-" + partner.code());
            if (current.isPresent() && profiles.projectedVersion(current.get()) >= partner.version()) {
                var id = current.get().path("profileIdList").path(0).path("id").asText();
                return List.of(new Variable(ProcessVariables.PROFILE_OUTCOME, Outcome.STALE.name()),
                        new Variable(ProcessVariables.PMS_PROFILE_IDS, id), new Variable("pmsProfileType", profileType));
            }
            var ensured = profiles.ensurePartner(hotel, partner, profileType);
            log.info("Partner {} v{} is profile {} ({})", partner.code(), partner.version(), ensured.profileId(), profileType);
            return List.of(new Variable(ProcessVariables.PROFILE_OUTCOME, Outcome.OK.name()),
                    new Variable(ProcessVariables.PMS_PROFILE_IDS, ensured.profileId()),
                    new Variable("pmsProfileType", profileType));
        } catch (PmsRejectedException e) {
            integration.await(var(task, ProcessVariables.PROCESS_KEY), var(task, ProcessVariables.DEFINITION_ID), null,
                    partner.code(), task.variables(), List.of(Cause.pmsRejectedPartner(partner.code(), e.getMessage())));
            return outcome(ProcessVariables.PROFILE_OUTCOME, Outcome.WAIT);
        }
    }

    /**
     * When Opera owns the partners: nothing is written, the profile is the one the import from Opera
     * recorded. A partner the ERP has and Opera does not waits for it — someone creates it in Opera
     * and the next import brings it.
     */
    List<Variable> resolvePartnerProfile(TaskExecutionRequested task, Partner partner) {
        var known = integration.partnerProfile(partner.code());
        if (known.isPresent()) {
            return List.of(new Variable(ProcessVariables.PROFILE_OUTCOME, Outcome.STALE.name()),
                    new Variable(ProcessVariables.PMS_PROFILE_IDS, known.get().pmsProfileId()),
                    new Variable("pmsProfileType", known.get().profileType()));
        }
        integration.await(var(task, ProcessVariables.PROCESS_KEY), var(task, ProcessVariables.DEFINITION_ID), null,
                partner.code(), task.variables(), List.of(Cause.missingPartner(partner.code())));
        return outcome(ProcessVariables.PROFILE_OUTCOME, Outcome.WAIT);
    }

    /**
     * The hotel a chain-level call is made "from". OHIP demands a hotel header even on the profiles
     * API, where the profile belongs to no hotel.
     */
    List<Variable> locked(TaskExecutionRequested task, Function<TaskExecutionRequested, List<Variable>> step) {
        return locks.withLock(var(task, ProcessVariables.HOTEL_CODE), var(task, ProcessVariables.LOCATOR), () -> step.apply(task));
    }

    /**
     * Any property whose connection has been verified: profiles are the chain's, and every hotel of
     * the chain lives in the same Opera environment (R24). None yet is transient — the partner is
     * projected once the first integration is past its connectivity check.
     */
    String anyHotel() {
        return connections.integrations().stream()
                .filter(i -> !Set.of(IntegrationStatus.CREATED, IntegrationStatus.CONNECTIVITY_FAILED,
                        IntegrationStatus.DECOMMISSIONED).contains(i.status()))
                .map(IntegrationView::pmsHotelCode)
                .sorted()
                .findFirst()
                .orElseThrow(() -> new PmsTransientException("No integration with a verified connection to Opera yet"));
    }

    /** The PMS hotel of the reservation, or null having registered that its equivalence is missing. */
    String resolveHotel(TaskExecutionRequested task, Reservation r) {
        var resolved = integration.resolve(r.hotelCode(), List.of(new CodeRef(CodeType.HOTEL, r.hotelCode())));
        if (!resolved.missing().isEmpty()) {
            await(task, r, resolved.missing());
            return null;
        }
        return resolved.target(CodeType.HOTEL, r.hotelCode());
    }

    List<Variable> rejected(TaskExecutionRequested task, Reservation r, String what, PmsRejectedException e, String outcomeVariable) {
        log.warn("Opera refused the {}: {} ({})", what, e.getMessage(), e.errorCode());
        await(task, r, List.of(Cause.pmsRejectedReservation(r.hotelCode(), r.locator(), task.stepId(),
                "%s — %s".formatted(e.getMessage(), e.errorCode()))));
        return outcome(outcomeVariable, Outcome.WAIT);
    }

    void await(TaskExecutionRequested task, Reservation r, List<Cause> causes) {
        integration.await(var(task, ProcessVariables.PROCESS_KEY), var(task, ProcessVariables.DEFINITION_ID),
                r.hotelCode(), r.locator(), task.variables(), causes);
    }

    Reservation reservation(TaskExecutionRequested task) {
        return integration.reservation(var(task, ProcessVariables.HOTEL_CODE), var(task, ProcessVariables.LOCATOR));
    }

    static List<Variable> outcome(String variable, Outcome outcome) {
        return List.of(new Variable(variable, outcome.name()));
    }

    static String var(TaskExecutionRequested task, String name) {
        return task.variables().stream().filter(v -> name.equals(v.name())).map(Variable::value)
                .filter(v -> v != null && !v.isBlank()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Step %s needs the variable %s".formatted(task.stepId(), name)));
    }
}
