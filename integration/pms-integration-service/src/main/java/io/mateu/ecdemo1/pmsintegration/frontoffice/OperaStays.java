package io.mateu.ecdemo1.pmsintegration.frontoffice;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.integration.model.pms.PmsReservationStamp;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.ohip.OhipClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Opera's reservations of a property as the front office consumes them — read only, never written
 * here: one reservation whole, and the property's reservations of a window, each with when Opera
 * last modified it.
 *
 * <p>How a change made in Opera itself is found. OHIP's reservation search has no «modified since»
 * filter (checked against OHIP UAT, 2026-09-27: {@code lastModifiedStartDate} and the like are
 * ignored, and no search type means «changed»), and its business events are a queue a tenant must
 * subscribe an external system to — a configuration of Opera this integration does not make. What
 * the search does answer, for every reservation it lists, is its {@code lastModifyDateTime}. So the
 * window — the stays in the house and those arriving within the integration's horizon — is paged
 * through, cancelled ones included, and what was modified at or after the cursor is what changed.
 * Two hundred to a page, a few pages for a hotel: a poll every minute.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class OperaStays {

    public enum Scope {
        /** Every reservation of the property: those born in Opera too. */
        ALL,
        /** Only those the chain's integration wrote: Opera's «Custom Reference». */
        CHAIN
    }

    static final int PAGE = 200;
    static final int MAX_PAGES = 25;

    final OhipClient ohip;
    final OhipProperties properties;
    /** The run's context in Opera, swapped at runtime by reset-demo. */
    final io.mateu.ecdemo1.pmsintegration.config.OperaContext context;

    public OperaStays(OhipClient ohip, OhipProperties properties) {
        this(ohip, properties, io.mateu.ecdemo1.pmsintegration.config.OperaContext.of(properties));
    }

    /**
     * The reservation, whole — with its packages, which a plain read leaves out. Empty if Opera does
     * not have it.
     */
    public Optional<JsonNode> byId(String hotelId, String reservationId) {
        return ohip.find(hotelId, "/rsv/v1/hotels/{h}/reservations/{id}?fetchInstructions=Reservation&fetchInstructions=Packages",
                        hotelId, reservationId)
                .map(body -> body.path("reservations").path("reservation"))
                .filter(list -> list.isArray() && !list.isEmpty())
                .map(list -> list.get(0));
    }

    /**
     * The property's reservations in the house or arriving between {@code from} and {@code to} —
     * those that leave on or after {@code from} and arrive by {@code to} — modified at or after
     * {@code modifiedSince} (all of them when it is null), oldest modification first.
     */
    public List<PmsReservationStamp> window(String hotelId, LocalDate from, LocalDate to, Scope scope, String modifiedSince) {
        var stamps = new ArrayList<PmsReservationStamp>();
        var offset = 0;
        for (var page = 0; page < MAX_PAGES; page++) {
            var uri = "/rsv/v1/hotels/{h}/reservations?limit={l}&offset={o}&departureStartDate={f}&arrivalEndDate={t}"
                    + (scope == Scope.CHAIN ? "&customReference={c}" : "");
            var answer = scope == Scope.CHAIN
                    ? ohip.get(hotelId, uri, hotelId, PAGE, offset, from, to, context.customReference()).body()
                    : ohip.get(hotelId, uri, hotelId, PAGE, offset, from, to).body();
            var reservations = answer.path("reservations");
            var infos = reservations.path("reservationInfo");
            for (var info : infos) {
                var stamp = stamp(info);
                if (modifiedSince == null || modifiedSince.isBlank() || stamp.lastModified().compareTo(modifiedSince) >= 0) {
                    stamps.add(stamp);
                }
            }
            var next = reservations.path("offset").asInt(offset + PAGE);
            if (!reservations.path("hasMore").asBoolean(false) || infos.isEmpty() || next <= offset) {
                break;
            }
            offset = next;
        }
        stamps.sort(Comparator.comparing(PmsReservationStamp::lastModified).thenComparing(PmsReservationStamp::pmsReservationId));
        return stamps;
    }

    static PmsReservationStamp stamp(JsonNode info) {
        var stay = info.path("roomStay");
        return new PmsReservationStamp(id(info, "Reservation"), id(info, "Confirmation"),
                date(stay.path("arrivalDate")), date(stay.path("departureDate")),
                info.path("reservationStatus").asText(null),
                StayMapper.isoLocal(info.path("lastModifyDateTime").asText(info.path("createDateTime").asText(""))));
    }

    static String id(JsonNode reservation, String type) {
        for (var id : reservation.path("reservationIdList")) {
            if (type.equals(id.path("type").asText())) {
                return id.path("id").asText();
            }
        }
        return "Reservation".equals(type) ? reservation.path("reservationIdList").path(0).path("id").asText() : null;
    }

    static LocalDate date(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? LocalDate.parse(node.asText().substring(0, 10)) : null;
    }
}
