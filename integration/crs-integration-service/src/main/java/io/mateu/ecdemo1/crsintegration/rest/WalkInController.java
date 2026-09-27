package io.mateu.ecdemo1.crsintegration.rest;

import io.mateu.ecdemo1.crsintegration.source.CatalogView;
import io.mateu.ecdemo1.crsintegration.source.CrsSource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A guest with no reservation at the hotel's desk. The CRS owns every booking, so the front office
 * is one more channel of it (WALKIN): it asks the CRS what the stay costs, and has the CRS make the
 * booking under the front office's own reference. The booking then goes down the chain as any other
 * — to Opera and back to the front office, which finds its stay by that reference.
 *
 * <p>The hotel speaks in one room and its people; this turns that into the CRS's booking.
 */
@RestController
@RequiredArgsConstructor
public class WalkInController {

    static final String CHANNEL = "WALKIN";

    final CrsSource crs;

    /** The person at the desk: the booking's holder, and its first guest. */
    public record Holder(String firstName, String lastName, String email, String phone, String nationality,
                         String documentType, String documentNumber) {
    }

    /**
     * @param reference     the front office's own — FO-… — sent again as the same booking on a retry
     * @param expectedTotal what the desk told the guest, from a quote: the CRS books it only at that
     */
    public record WalkIn(String hotelCode, String reference, LocalDate arrival, LocalDate departure,
                         String roomTypeCode, String ratePlanCode, String boardCode, int adults,
                         List<Integer> childrenAges, Holder holder, BigDecimal expectedTotal) {
    }

    public record Booked(String locator, String reference) {
    }

    public record Option(String code, String name) {
    }

    /** What the desk can choose from for a hotel: the CRS's codes, with their names. */
    public record Offer(String hotelCode, String hotelName, List<Option> roomTypes, List<Option> ratePlans,
                        List<Option> boards) {
    }

    @GetMapping("/walk-ins/offer")
    public Offer offer(@RequestParam String hotelCode) {
        var catalog = crs.catalog();
        var hotel = catalog.hotels().stream().filter(h -> hotelCode.equals(h.code())).findFirst()
                .orElseThrow(() -> new NoSuchElementException("The CRS has no hotel " + hotelCode));
        var codes = hotel.codes();
        return new Offer(hotel.code(), hotel.name(), options(hotel.roomTypes()),
                options(codes == null ? catalog.ratePlans() : codes.ratePlans()),
                options(codes == null ? catalog.boards() : codes.boards()));
    }

    @PostMapping("/walk-ins/quote")
    public Map<?, ?> quote(@RequestBody WalkIn walkIn) {
        return crs.quote(walkIn.hotelCode(), booking(walkIn, false));
    }

    @PostMapping("/walk-ins")
    @ResponseStatus(HttpStatus.CREATED)
    public Booked book(@RequestBody WalkIn walkIn) {
        if (walkIn.reference() == null || walkIn.reference().isBlank()) {
            throw new IllegalArgumentException("A walk-in needs the front office's reference");
        }
        if (walkIn.holder() == null) {
            throw new IllegalArgumentException("A walk-in needs its holder");
        }
        return new Booked(crs.create(walkIn.hotelCode(), booking(walkIn, true), walkIn.expectedTotal()), walkIn.reference());
    }

    /** The CRS's booking request for the walk-in: one room, the holder its first guest. */
    static Map<String, Object> booking(WalkIn w, boolean withHolder) {
        var room = new HashMap<String, Object>();
        room.put("roomTypeCode", w.roomTypeCode());
        room.put("ratePlanCode", w.ratePlanCode());
        room.put("boardCode", w.boardCode());
        room.put("adults", w.adults());
        room.put("childrenAges", w.childrenAges() == null ? List.of() : w.childrenAges());
        var guests = new ArrayList<Map<String, Object>>();
        var booking = new HashMap<String, Object>();
        booking.put("channelCode", CHANNEL);
        booking.put("externalReference", w.reference());
        booking.put("arrival", w.arrival());
        booking.put("departure", w.departure());
        var h = w.holder();
        if (withHolder && h != null) {
            var holder = new HashMap<String, Object>();
            holder.put("firstName", h.firstName());
            holder.put("lastName", h.lastName());
            holder.put("email", h.email());
            holder.put("phone", h.phone());
            holder.put("nationality", h.nationality());
            booking.put("holder", holder);
            var guest = new HashMap<String, Object>();
            guest.put("firstName", h.firstName());
            guest.put("lastName", h.lastName());
            guest.put("type", "Adult");
            guest.put("nationality", h.nationality());
            guest.put("documentType", h.documentType());
            guest.put("documentNumber", h.documentNumber());
            guests.add(guest);
        }
        room.put("guests", guests);
        booking.put("rooms", List.of(room));
        booking.put("comments", "Walk-in en recepción");
        return booking;
    }

    static List<Option> options(List<CatalogView.Code> codes) {
        return codes == null ? List.of() : codes.stream().map(c -> new Option(c.code(), c.name())).toList();
    }

    /** The CRS's refusal — an unknown code, a price that changed — reaches the desk as the CRS said it. */
    @ExceptionHandler(HttpClientErrorException.class)
    ProblemDetail refused(HttpClientErrorException e) {
        var detail = e.getResponseBodyAs(ProblemDetail.class);
        return ProblemDetail.forStatusAndDetail(e.getStatusCode(),
                detail != null && detail.getDetail() != null ? detail.getDetail() : e.getStatusText());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
