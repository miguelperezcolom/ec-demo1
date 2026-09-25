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
    final io.mateu.ecdemo1.pmsintegration.frontoffice.FrontOfficeWriter frontOffice;

    public Map<String, Function<TaskExecutionRequested, List<Variable>>> handlers() {
        return Map.of(
                "ensure-guest-profile", this::ensureGuestProfile,
                "upsert-reservation", task -> locked(task, this::upsertReservation),
                "cancel-reservation", task -> locked(task, this::cancelReservation),
                "ensure-partner-profile", this::ensurePartnerProfile,
                "write-front-office", task -> locked(task, frontOffice::write),
                "cancel-front-office", task -> locked(task, frontOffice::cancel));
    }

    List<Variable> ensureGuestProfile(TaskExecutionRequested task) {
        var r = reservation(task);
        var hotel = resolveHotel(task, r);
        if (hotel == null) {
            return outcome(ProcessVariables.PROFILE_OUTCOME, Outcome.WAIT);
        }
        var customerId = holderCustomer(r);
        try {
            var existing = reservations.byLocator(hotel, r.locator());
            if (existing.isPresent() && !rewritesTheGuest(task)
                    && (reservations.writtenVersion(existing.get()) >= r.version() || OperaReservations.cancelled(existing.get()))) {
                // Opera already holds this version: the reservation will not be written, and neither is
                // its guest. A merge in the MDM is the exception — it projects the same version again
                // precisely to put the new customer code on the profile.
                var id = OperaReservations.guestProfileId(existing.get());
                if (id.isPresent()) {
                    log.info("{} v{}: Opera already holds it; guest profile {} left as it is", r.locator(), r.version(), id.get());
                    var variables = new ArrayList<>(List.of(new Variable(ProcessVariables.PROFILE_OUTCOME, Outcome.OK.name()),
                            new Variable(ProcessVariables.GUEST_PROFILE_ID, id.get())));
                    if (customerId != null) {
                        variables.add(new Variable(ProcessVariables.CUSTOMER_ID, customerId));
                    }
                    return variables;
                }
            }
            var known = ohipProperties.profileReferences() ? null
                    : existing.flatMap(OperaReservations::guestProfileId).orElse(null);
            // The guest as Salesforce — the master — has them, through the MDM; the reservation's data
            // for what the MDM does not know, or if it does not answer.
            var holder = customerId == null ? r.holder() : integration.customer(customerId)
                    .map(master -> overlay(master, r.holder())).orElse(r.holder());
            var ensured = profiles.ensureGuest(hotel, r.locator(), holder, customerId, known);
            if (customerId != null) {
                integration.xref(customerId, "OPERA", ensured.profileId(), hotel + "/" + r.locator());
            }
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
        var codes = codesOf(r);
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
     * Each night's base as the amount itself. On a modification OPERA re-rates the night from its own
     * rate plan and keeps the amount sent only as a share of that base — a 25% fee of the CRS's price
     * came out as 25% of Opera's. With the base given, the night costs what is sent.
     */
    static com.fasterxml.jackson.databind.JsonNode withBaseAmounts(com.fasterxml.jackson.databind.JsonNode node) {
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            var base = object.get("base");
            if (base instanceof com.fasterxml.jackson.databind.node.ObjectNode b && b.has("amountBeforeTax")) {
                b.set("baseAmount", b.get("amountBeforeTax"));
            }
            object.forEach(TaskHandlers::withBaseAmounts);
        } else if (node != null && node.isArray()) {
            node.forEach(TaskHandlers::withBaseAmounts);
        }
        return node;
    }

    /** The codes a reservation is written to Opera with: hotel, channel, each room's, each payment's. */
    static LinkedHashSet<CodeRef> codesOf(Reservation r) {
        var codes = new LinkedHashSet<CodeRef>();
        codes.add(new CodeRef(CodeType.HOTEL, r.hotelCode()));
        codes.add(new CodeRef(CodeType.CHANNEL, r.channelCode()));
        r.rooms().forEach(room -> {
            codes.add(new CodeRef(CodeType.ROOM_TYPE, room.roomTypeCode()));
            codes.add(new CodeRef(CodeType.RATE_PLAN, room.ratePlanCode()));
            codes.add(new CodeRef(CodeType.BOARD, room.boardCode()));
        });
        r.payments().forEach(p -> codes.add(new CodeRef(CodeType.PAYMENT_METHOD, p.methodCode())));
        return codes;
    }

    /**
     * The reservation as it costs its no-show fee: every night's rate by the same share, the last one
     * taking the rounding, so that the stay adds up to the fee exactly.
     */
    public static Reservation withFee(Reservation r) {
        var fee = r.cancellationFee();
        var original = r.originalAmount() == null || r.originalAmount().signum() == 0 ? null : r.originalAmount();
        if (fee == null || original == null) {
            return r;
        }
        var share = fee.divide(original, 10, java.math.RoundingMode.HALF_UP);
        var rooms = new ArrayList<io.mateu.ecdemo1.integration.model.reservation.Room>();
        var written = java.math.BigDecimal.ZERO;
        io.mateu.ecdemo1.integration.model.reservation.NightlyRate last = null;
        int lastRoom = -1, lastNight = -1;
        for (var room : r.rooms()) {
            var nights = new ArrayList<io.mateu.ecdemo1.integration.model.reservation.NightlyRate>();
            for (var night : room.nightlyRates()) {
                var amount = night.amount().multiply(share).setScale(2, java.math.RoundingMode.HALF_UP);
                written = written.add(amount);
                nights.add(new io.mateu.ecdemo1.integration.model.reservation.NightlyRate(night.date(), amount));
            }
            rooms.add(new io.mateu.ecdemo1.integration.model.reservation.Room(room.line(), room.roomTypeCode(),
                    room.ratePlanCode(), room.boardCode(), room.adults(), room.childrenAges(), room.guests(), nights));
            if (!nights.isEmpty()) {
                lastRoom = rooms.size() - 1;
                lastNight = nights.size() - 1;
                last = nights.get(lastNight);
            }
        }
        if (last != null && written.compareTo(fee) != 0) {
            var room = rooms.get(lastRoom);
            var nights = new ArrayList<>(room.nightlyRates());
            nights.set(lastNight, new io.mateu.ecdemo1.integration.model.reservation.NightlyRate(last.date(),
                    last.amount().add(fee.subtract(written))));
            rooms.set(lastRoom, new io.mateu.ecdemo1.integration.model.reservation.Room(room.line(), room.roomTypeCode(),
                    room.ratePlanCode(), room.boardCode(), room.adults(), room.childrenAges(), room.guests(), nights));
        }
        return new Reservation(r.hotelCode(), r.locator(), r.version(), r.status(), r.channelCode(), r.partnerCode(),
                r.externalReference(), r.arrival(), r.departure(), r.currency(), r.holder(), rooms, r.payments(), fee,
                r.comments(), r.cancellationReasonCode(), r.cancellationFee(), r.originalAmount());
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
            if (r.noShow() && r.cancellationFee() != null) {
                // A no-show still costs its fee: Opera's reservation says so before it is cancelled — once
                // cancelled, Opera takes no more changes.
                var all = new ArrayList<>(codesOf(r));
                all.add(new CodeRef(CodeType.CANCELLATION_REASON, r.cancellationReasonCode()));
                var full = integration.resolve(r.hotelCode(), all);
                if (!full.missing().isEmpty()) {
                    await(task, r, full.missing());
                    return outcome(ProcessVariables.WRITE_OUTCOME, Outcome.WAIT);
                }
                var partner = r.partnerCode() == null ? null : integration.partner(r.partnerCode());
                var partnerProfile = r.partnerCode() == null ? null : integration.partnerProfile(r.partnerCode()).orElse(null);
                var charged = withFee(r);
                var body = payload.build(charged, full, hotel,
                        OperaReservations.guestProfileId(existing.get()).orElse(null), partner,
                        partnerProfile == null ? null : partnerProfile.pmsProfileId(),
                        partnerProfile == null ? null : partnerProfile.profileType());
                reservations.update(hotel, reservationId, withBaseAmounts(body));
                log.info("{}: a no-show — its fee of {} (of {}) written to {} before cancelling", r.locator(),
                        r.cancellationFee(), r.originalAmount(), reservationId);
            }
            var reason = r.cancellationReasonCode() == null ? null
                    : resolved.target(CodeType.CANCELLATION_REASON, r.cancellationReasonCode());
            reservations.cancel(hotel, reservationId, reason, r.cancellationReasonCode() == null
                    ? "Cancelled in the CRS"
                    : r.noShow() ? "No show: cancelled in the CRS with a fee of %s %s (of %s)".formatted(
                            r.cancellationFee(), r.currency(), r.originalAmount())
                    : "Cancelled in the CRS (reason " + r.cancellationReasonCode() + ")");
            log.info("{} cancelled in {} ({})", r.locator(), hotel, reservationId);
            return List.of(new Variable(ProcessVariables.WRITE_OUTCOME, Outcome.DONE.name()),
                    new Variable(ProcessVariables.PMS_RESERVATION_ID, reservationId));
        } catch (PmsRejectedException e) {
            return rejected(task, r, "cancellation of " + r.locator(), e, ProcessVariables.WRITE_OUTCOME);
        }
    }

    /**
     * The partner as a profile in Opera — «Proyectar Interlocutor», from the ERP to Opera. What the ERP
     * already records as its Opera profile is used as it is: nothing is written. Otherwise Opera is
     * asked first, by the partner's CorporateId, in case it has it and the ERP does not know; and only
     * if it does not, the profile is created. Whichever it is, the next step writes it back to the ERP,
     * so it is never created twice.
     */
    List<Variable> ensurePartnerProfile(TaskExecutionRequested task) {
        var partner = integration.partner(var(task, ProcessVariables.PARTNER_CODE));
        if (partner.pmsProfileId() != null && !partner.pmsProfileId().isBlank()) {
            log.info("Partner {} is already profile {} in Opera, as the ERP records", partner.code(), partner.pmsProfileId());
            return profiled(Outcome.STALE, partner.pmsProfileId(), partner.pmsProfileType());
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
            if (!ohipProperties.profileReferences()) {
                var found = profiles.byCorporateId(hotel, partner.code(), profileType);
                if (found.isPresent()) {
                    log.info("Partner {} found in Opera by its CorporateId: profile {}", partner.code(), found.get());
                    return profiled(Outcome.STALE, found.get(), profileType);
                }
            } else {
                var current = profiles.byExternalId(hotel, "PARTNER-" + partner.code());
                if (current.isPresent() && profiles.projectedVersion(current.get()) >= partner.version()) {
                    return profiled(Outcome.STALE, current.get().path("profileIdList").path(0).path("id").asText(), profileType);
                }
            }
            var ensured = profiles.ensurePartner(hotel, partner, profileType);
            log.info("Partner {} v{} is profile {} ({}){}", partner.code(), partner.version(), ensured.profileId(), profileType,
                    ensured.created() ? ", created in Opera" : "");
            return profiled(Outcome.OK, ensured.profileId(), profileType);
        } catch (PmsRejectedException e) {
            integration.await(var(task, ProcessVariables.PROCESS_KEY), var(task, ProcessVariables.DEFINITION_ID), null,
                    partner.code(), task.variables(), List.of(Cause.pmsRejectedPartner(partner.code(), e.getMessage())));
            return outcome(ProcessVariables.PROFILE_OUTCOME, Outcome.WAIT);
        }
    }

    static List<Variable> profiled(Outcome outcome, String profileId, String profileType) {
        return List.of(new Variable(ProcessVariables.PROFILE_OUTCOME, outcome.name()),
                new Variable(ProcessVariables.PMS_PROFILE_IDS, profileId),
                new Variable("pmsProfileType", profileType == null ? "" : profileType));
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

    /** Whether this projection exists to rewrite the guest: a merge or a change of the customer in the MDM. */
    static boolean rewritesTheGuest(TaskExecutionRequested task) {
        return origin(task).startsWith("mdm-");
    }

    /** The master's data, and the reservation's where the master has none. */
    public static Person overlay(Person master, Person reservation) {
        if (reservation == null) {
            return master;
        }
        return new Person(or(master.firstName(), reservation.firstName()), or(master.lastName(), reservation.lastName()),
                reservation.type(), reservation.age(), or(master.email(), reservation.email()), or(master.phone(), reservation.phone()),
                or(master.nationality(), reservation.nationality()),
                master.birthDate() != null ? master.birthDate() : reservation.birthDate(),
                or(master.documentType(), reservation.documentType()), or(master.documentNumber(), reservation.documentNumber()));
    }

    static String or(String value, String otherwise) {
        return value == null || value.isBlank() ? otherwise : value;
    }

    static String origin(TaskExecutionRequested task) {
        return task.variables().stream().filter(v -> ProcessVariables.ORIGIN.equals(v.name())).map(Variable::value)
                .filter(v -> v != null).findFirst().orElse("");
    }

    static String var(TaskExecutionRequested task, String name) {
        return task.variables().stream().filter(v -> name.equals(v.name())).map(Variable::value)
                .filter(v -> v != null && !v.isBlank()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Step %s needs the variable %s".formatted(task.stepId(), name)));
    }
}
