package io.mateu.ecdemo1.mapping.prepare;

import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.integration.model.process.Outcome;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.store.PartnerProfileRepository;
import io.mateu.workflow.dtos.Variable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * The «Preparar» step of the HLA: local, it does not touch the PMS. It resolves every translation
 * the write will need, all at once, and answers OK — or registers the complete list of what is
 * missing and answers WAIT. All at once on purpose: a reservation missing three equivalences blocks
 * once with three causes, not three times one after the other.
 */
@Service
@RequiredArgsConstructor
public class Preparation {

    final Dictionary dictionary;
    final Causes causes;
    final PartnerProfileRepository partnerProfiles;
    final IntegrationClients integrations;

    public record Code(CodeType type, String code) {
    }

    /**
     * @param origin who started the process when it was not a change in the CRS — a backfill —
     *               or null
     */
    public record WaitContext(String processKey, String definitionId, String subject, List<Variable> variables,
                              String origin) {

        /** A backfill projects before the integration is active: that is how the hotel gets ready. */
        boolean heldByActivation() {
            return origin == null || !origin.startsWith("backfill");
        }
    }

    @Transactional
    public Outcome reservation(Reservation r, WaitContext wait) {
        var codes = new LinkedHashSet<Code>();
        codes.add(new Code(CodeType.HOTEL, r.hotelCode()));
        codes.add(new Code(CodeType.CHANNEL, r.channelCode()));
        r.rooms().forEach(room -> {
            codes.add(new Code(CodeType.ROOM_TYPE, room.roomTypeCode()));
            codes.add(new Code(CodeType.RATE_PLAN, room.ratePlanCode()));
            codes.add(new Code(CodeType.BOARD, room.boardCode()));
        });
        r.payments().forEach(p -> codes.add(new Code(CodeType.PAYMENT_METHOD, p.methodCode())));
        return check(r.hotelCode(), codes, r.partnerCode(), wait, wait.heldByActivation());
    }

    @Transactional
    public Outcome cancellation(Reservation r, WaitContext wait) {
        var codes = new LinkedHashSet<Code>();
        codes.add(new Code(CodeType.HOTEL, r.hotelCode()));
        if (r.cancellationReasonCode() != null) {
            codes.add(new Code(CodeType.CANCELLATION_REASON, r.cancellationReasonCode()));
        }
        return check(r.hotelCode(), codes, null, wait, true);
    }

    /** Partners are chain-level: their causes carry no hotel. */
    @Transactional
    public Outcome partner(Partner p, WaitContext wait) {
        var codes = new LinkedHashSet<Code>();
        codes.add(new Code(CodeType.PARTNER_TYPE, p.type().name()));
        return check(null, codes, null, wait, false);
    }

    /**
     * @param gated whether the hotel's integration has to be active for this to go on. Partners are
     *              the chain's, and a backfill is what gets an integration ready, so neither is.
     */
    private Outcome check(String hotelCode, LinkedHashSet<Code> codes, String partnerCode, WaitContext wait,
                          boolean gated) {
        var scope = hotelCode == null ? "chain" : hotelCode;
        var missing = new java.util.ArrayList<Cause>();
        var stillMissing = new java.util.ArrayList<Supplier<Boolean>>();
        if (gated && !flows(hotelCode)) {
            missing.add(Cause.integrationInactive(hotelCode));
            stillMissing.add(() -> !flows(hotelCode));
        }
        for (var code : codes) {
            if (dictionary.resolve(hotelCode, code.type(), code.code()).isEmpty()) {
                missing.add(Cause.missingMapping(scope, code.type(), code.code()));
                stillMissing.add(() -> dictionary.resolve(hotelCode, code.type(), code.code()).isEmpty());
            }
        }
        if (partnerCode != null && !partnerProfiles.existsById(partnerCode)) {
            missing.add(Cause.missingPartner(partnerCode));
            stillMissing.add(() -> !partnerProfiles.existsById(partnerCode));
        }
        if (missing.isEmpty()) {
            return Outcome.OK;
        }
        causes.await(wait.processKey(), wait.definitionId(), hotelCode, wait.subject(), wait.variables(), missing);
        // An equivalence approved between the check above and the wait being registered would leave
        // this process waiting on a cause nothing will resolve again. Look once more, now that the
        // wait is visible to whoever approves.
        for (int i = 0; i < missing.size(); i++) {
            if (!stillMissing.get(i).get()) {
                causes.resolveIfOpen(missing.get(i).key(), "system");
            }
        }
        return Outcome.WAIT;
    }

    /** Whether the hotel's reservations may reach the PMS in real time: an integration, and an active one. */
    boolean flows(String hotelCode) {
        return integrations.integration(hotelCode).map(i -> i.status().flows()).orElse(false);
    }
}
