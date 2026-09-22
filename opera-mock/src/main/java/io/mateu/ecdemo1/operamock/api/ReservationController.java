package io.mateu.ecdemo1.operamock.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mateu.ecdemo1.operamock.config.OperaCatalog;
import io.mateu.ecdemo1.operamock.config.OperaMockProperties;
import io.mateu.ecdemo1.operamock.store.OperaStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * rsv (reservations) and the slice of csh (deposit payments) the adapter uses.
 *
 * <p>Unlike the Distribution API the HLA planned on — which records a reservation already committed
 * elsewhere without looking — the Property API validates what it is given against the property:
 * codes, the guest profile, the dates and availability. The double does the same, because whether
 * that validation gets in the way is one of the things the PoC has to find out (R19).
 */
@RestController
@RequiredArgsConstructor
public class ReservationController {

    final OperaStore store;
    final OperaCatalog catalog;
    final OperaMockProperties properties;
    final ObjectMapper objectMapper;
    final Clock clock;

    @PostMapping("/rsv/v1/hotels/{hotelId}/reservations")
    public ResponseEntity<Map<String, Object>> create(@PathVariable String hotelId, @RequestBody JsonNode body) {
        var reservation = first(body);
        var id = store.nextId();
        validate(hotelId, reservation, null);
        stamp(reservation, hotelId, id, "C" + id);
        reservation.put("reservationStatus", "Reserved");
        reservation.put("createDateTime", clock.instant().toString());
        store.putReservation(id, reservation);
        var href = "/rsv/v1/hotels/%s/reservations/%s".formatted(hotelId, id);
        return ResponseEntity.created(URI.create(href))
                .body(Map.of("links", List.of(Map.of("href", href, "rel", "self", "method", "GET"))));
    }

    @GetMapping("/rsv/v1/hotels/{hotelId}/reservations")
    public Map<String, Object> search(@PathVariable String hotelId,
                                      @RequestParam(required = false) List<String> externalReferenceIds,
                                      @RequestParam(required = false) List<String> externalSystemCodes) {
        var found = store.reservations().stream()
                .filter(r -> hotelId.equals(r.path("hotelId").asText()))
                .filter(r -> externalReferenceIds == null || StreamSupport.stream(r.path("externalReferences").spliterator(), false)
                        .anyMatch(x -> externalReferenceIds.contains(x.path("id").asText())
                                && (externalSystemCodes == null || externalSystemCodes.contains(x.path("idContext").asText()))))
                .toList();
        return Map.of("reservations", Map.of("reservation", found, "totalResults", found.size(), "count", found.size(),
                "hasMore", false));
    }

    @GetMapping("/rsv/v1/hotels/{hotelId}/reservations/{reservationId}")
    public Map<String, Object> get(@PathVariable String hotelId, @PathVariable String reservationId) {
        return Map.of("reservations", Map.of("reservation", List.of(existing(hotelId, reservationId))));
    }

    /** Replaces the reservation with what is sent — the state, whole — keeping its ids. */
    @PutMapping("/rsv/v1/hotels/{hotelId}/reservations/{reservationId}")
    public Map<String, Object> update(@PathVariable String hotelId, @PathVariable String reservationId,
                                      @RequestBody JsonNode body) {
        var current = existing(hotelId, reservationId);
        if ("Cancelled".equals(current.path("reservationStatus").asText())) {
            throw OperaError.badRequest("MOCK-CANCELLED", "Reservation %s is cancelled and cannot be updated".formatted(reservationId));
        }
        var reservation = first(body);
        validate(hotelId, reservation, reservationId);
        stamp(reservation, hotelId, reservationId, confirmation(current));
        reservation.put("reservationStatus", "Reserved");
        reservation.put("createDateTime", current.path("createDateTime").asText());
        reservation.put("lastModifyDateTime", clock.instant().toString());
        store.putReservation(reservationId, reservation);
        return Map.of("links", List.of(Map.of("href", "/rsv/v1/hotels/%s/reservations/%s".formatted(hotelId, reservationId),
                "rel", "self")));
    }

    @PostMapping("/rsv/v1/hotels/{hotelId}/reservations/{reservationId}/cancellations")
    public Map<String, Object> cancel(@PathVariable String hotelId, @PathVariable String reservationId,
                                      @RequestBody JsonNode body) {
        var current = existing(hotelId, reservationId);
        var reason = body.path("reason").path("code").asText();
        var property = property(hotelId);
        if (!property.has(property.cancellationCodes(), reason)) {
            throw OperaError.badRequest("MOCK-CXLCODE", "Invalid cancellation code " + reason);
        }
        if ("Cancelled".equals(current.path("reservationStatus").asText())) {
            throw OperaError.badRequest("MOCK-ALREADY-CANCELLED", "Reservation %s is already cancelled".formatted(reservationId));
        }
        var number = "X" + reservationId;
        current.put("reservationStatus", "Cancelled");
        current.putObject("cancellation").put("cancellationNo", number).put("reason", reason)
                .put("date", clock.instant().toString());
        return Map.of("cxlActivityLog", List.of(Map.of("cancellationNo", Map.of("id", number, "type", "Cancellation"),
                "reservationId", Map.of("id", reservationId, "type", "Reservation"))));
    }

    @PostMapping("/csh/v1/hotels/{hotelId}/reservations/{reservationId}/depositPayments")
    public ResponseEntity<Map<String, Object>> deposit(@PathVariable String hotelId, @PathVariable String reservationId,
                                                       @RequestBody ObjectNode body) {
        existing(hotelId, reservationId);
        var method = body.path("paymentMethod").path("paymentMethod").asText();
        var property = property(hotelId);
        if (!property.has(property.paymentMethods(), method)) {
            throw OperaError.badRequest("MOCK-PAYMETHOD", "Invalid payment method " + method);
        }
        body.put("postingDate", LocalDate.now(clock).toString());
        store.addDeposit(reservationId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("links", List.of()));
    }

    @GetMapping("/csh/v1/hotels/{hotelId}/reservations/{reservationId}/depositPayments")
    public Map<String, Object> deposits(@PathVariable String hotelId, @PathVariable String reservationId) {
        existing(hotelId, reservationId);
        return Map.of("depositPayments", store.deposits(reservationId));
    }

    ObjectNode existing(String hotelId, String reservationId) {
        return store.reservation(reservationId).filter(r -> hotelId.equals(r.path("hotelId").asText()))
                .orElseThrow(() -> OperaError.notFound("Reservation %s not found in %s".formatted(reservationId, hotelId)));
    }

    OperaCatalog.Property property(String hotelId) {
        return catalog.property(hotelId).orElseThrow(() -> OperaError.notFound("No property " + hotelId));
    }

    static String confirmation(ObjectNode reservation) {
        for (var id : reservation.path("reservationIdList")) {
            if ("Confirmation".equals(id.path("type").asText())) {
                return id.path("id").asText();
            }
        }
        return null;
    }

    ObjectNode first(JsonNode body) {
        var list = body.path("reservations").path("reservation");
        if (!list.isArray() || list.isEmpty() || !list.get(0).isObject()) {
            throw OperaError.badRequest("MOCK-BODY", "reservations.reservation[0] is required");
        }
        return (ObjectNode) list.get(0).deepCopy();
    }

    void stamp(ObjectNode reservation, String hotelId, String id, String confirmation) {
        reservation.put("hotelId", hotelId);
        ArrayNode ids = reservation.putArray("reservationIdList");
        ids.addObject().put("id", id).put("type", "Reservation");
        ids.addObject().put("id", confirmation).put("type", "Confirmation");
    }

    /** What the Property API refuses: codes the property does not have, an unknown guest, no rooms left. */
    void validate(String hotelId, ObjectNode r, String excludeId) {
        var property = property(hotelId);
        var stay = r.path("roomStay");
        LocalDate arrival;
        LocalDate departure;
        try {
            arrival = LocalDate.parse(stay.path("arrivalDate").asText());
            departure = LocalDate.parse(stay.path("departureDate").asText());
        } catch (RuntimeException e) {
            throw OperaError.badRequest("MOCK-DATES", "roomStay.arrivalDate and departureDate are required");
        }
        if (!departure.isAfter(arrival)) {
            throw OperaError.badRequest("MOCK-DATES", "Departure must be after arrival");
        }
        var rates = stay.path("roomRates");
        if (!rates.isArray() || rates.isEmpty()) {
            throw OperaError.badRequest("MOCK-RATES", "roomStay.roomRates is required");
        }
        for (var rate : rates) {
            check(property.has(property.roomTypes(), rate.path("roomType").asText()), "Invalid room type " + rate.path("roomType").asText());
            check(property.has(property.ratePlans(), rate.path("ratePlanCode").asText()), "Invalid rate code " + rate.path("ratePlanCode").asText());
            check(property.has(property.marketCodes(), rate.path("marketCode").asText()), "Invalid market code " + rate.path("marketCode").asText());
            check(property.has(property.sourceCodes(), rate.path("sourceCode").asText()), "Invalid source code " + rate.path("sourceCode").asText());
        }
        for (var pkg : r.path("reservationPackages")) {
            check(property.has(property.packages(), pkg.path("packageCode").asText()), "Invalid package " + pkg.path("packageCode").asText());
        }
        for (var method : r.path("reservationPaymentMethods")) {
            check(property.has(property.paymentMethods(), method.path("paymentMethod").asText()),
                    "Invalid payment method " + method.path("paymentMethod").asText());
        }
        var guests = r.path("reservationGuests");
        check(guests.isArray() && !guests.isEmpty(), "A reservation needs a guest profile");
        for (var guest : guests) {
            var profileId = guest.path("profileInfo").path("profileIdList").path(0).path("id").asText();
            check(store.profile(profileId).isPresent(), "Guest profile %s does not exist".formatted(profileId));
        }
        for (var profile : r.path("reservationProfiles").path("reservationProfile")) {
            var profileId = profile.path("profileIdList").path(0).path("id").asText();
            check(store.profile(profileId).isPresent(), "Profile %s does not exist".formatted(profileId));
        }
        availability(hotelId, rates, arrival, departure, excludeId);
    }

    void availability(String hotelId, JsonNode rates, LocalDate arrival, LocalDate departure, String excludeId) {
        var roomTypes = new ArrayList<String>();
        rates.forEach(rate -> roomTypes.add(rate.path("roomType").asText()));
        for (var roomType : roomTypes.stream().distinct().toList()) {
            for (var night = arrival; night.isBefore(departure); night = night.plusDays(1)) {
                var date = night;
                var taken = store.reservations().stream()
                        .filter(o -> hotelId.equals(o.path("hotelId").asText()))
                        .filter(o -> !"Cancelled".equals(o.path("reservationStatus").asText()))
                        .filter(o -> excludeId == null || !excludeId.equals(firstId(o)))
                        .filter(o -> covers(o, roomType, date))
                        .count();
                if (taken >= properties.capacityPerRoomType()) {
                    throw OperaError.badRequest("MOCK-NOAVAIL",
                            "No availability for room type %s on %s".formatted(roomType, date));
                }
            }
        }
    }

    static String firstId(JsonNode reservation) {
        return reservation.path("reservationIdList").path(0).path("id").asText();
    }

    static boolean covers(JsonNode reservation, String roomType, LocalDate night) {
        var stay = reservation.path("roomStay");
        var arrival = LocalDate.parse(stay.path("arrivalDate").asText());
        var departure = LocalDate.parse(stay.path("departureDate").asText());
        if (night.isBefore(arrival) || !night.isBefore(departure)) {
            return false;
        }
        for (var rate : stay.path("roomRates")) {
            if (roomType.equals(rate.path("roomType").asText())) {
                return true;
            }
        }
        return false;
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw OperaError.badRequest("MOCK-INVALID", message);
        }
    }
}
