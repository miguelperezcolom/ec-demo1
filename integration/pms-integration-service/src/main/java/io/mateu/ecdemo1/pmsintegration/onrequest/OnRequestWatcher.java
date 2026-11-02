package io.mateu.ecdemo1.pmsintegration.onrequest;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients.CodeRef;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaAvailability;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The reservations a person let in «on request» — Opera had no room for them — go into Opera by
 * themselves once it has. Every few minutes, for each open cause on request, each reservation waiting
 * on it is looked up in the CRS, its room types translated, and Opera asked whether it would sell
 * those rooms for its stay; the first that fits is taken off the refusal (releaseRefusal), and its
 * process is relaunched and writes it. One a cause a round: two that want the last room do not both
 * go and one come back refused; the next round sees what the first took. Reads only, but for the
 * release.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OnRequestWatcher {

    static final String STEP = "upsert-reservation";

    final IntegrationClients integration;
    final OperaAvailability availability;

    @Scheduled(fixedDelayString = "${pms-integration.on-request-check:2m}", initialDelayString = "${pms-integration.on-request-first-check:1m}")
    public void look() {
        List<IntegrationClients.OnRequestCause> causes;
        try {
            causes = integration.onRequestCauses();
        } catch (RuntimeException e) {
            log.debug("On request: the mapping did not answer: {}", e.getMessage());
            return;
        }
        for (var cause : causes) {
            try {
                letInTheFirstThatFits(cause);
            } catch (RuntimeException e) {
                log.warn("On request: {} not looked at: {}", cause.key(), e.getMessage());
            }
        }
    }

    /** The first reservation waiting on the cause that Opera has room for now, let in; true if one was. */
    boolean letInTheFirstThatFits(IntegrationClients.OnRequestCause cause) {
        var seen = new HashSet<String>();
        for (var waiting : integration.waitingOn(cause.key())) {
            if (waiting.subject() == null || !seen.add(waiting.hotelCode() + "/" + waiting.subject())) {
                continue;
            }
            if (fits(waiting.hotelCode(), waiting.subject())) {
                integration.releaseRefusal(waiting.hotelCode(), waiting.subject(), STEP, "on request: Opera has room now");
                log.info("On request: {}/{} fits in Opera now — let in", waiting.hotelCode(), waiting.subject());
                return true;
            }
        }
        return false;
    }

    /** Whether Opera would sell the reservation's rooms for its stay now. */
    boolean fits(String hotelCode, String locator) {
        var r = integration.reservation(hotelCode, locator);
        if (r == null || r.rooms() == null || r.rooms().isEmpty()) {
            return false;
        }
        var codes = new ArrayList<CodeRef>();
        codes.add(new CodeRef(CodeType.HOTEL, r.hotelCode()));
        r.rooms().forEach(room -> codes.add(new CodeRef(CodeType.ROOM_TYPE, room.roomTypeCode())));
        var resolved = integration.resolve(r.hotelCode(), codes);
        if (!resolved.missing().isEmpty()) {
            return false;
        }
        var pmsHotel = resolved.target(CodeType.HOTEL, r.hotelCode());
        var rooms = r.rooms().stream().map(room -> resolved.target(CodeType.ROOM_TYPE, room.roomTypeCode())).toList();
        return availability.fits(pmsHotel, r.arrival(), r.departure(), rooms);
    }
}
