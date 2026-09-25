package io.mateu.ecdemo1.pmsintegration.ohip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.reservation.Payment;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/** Reservations and deposits in Opera (rsv, csh), found by the CRS's locator. */
@Component
@RequiredArgsConstructor
public class OperaReservations {

    final OhipClient ohip;
    final OhipProperties properties;
    final ObjectMapper objectMapper;

    /**
     * The reservation the CRS knows by this locator, if Opera has it — whole. A search answers with
     * summaries ({@code reservationInfo}), which carry no UDFs, so the one found is read again by id:
     * the version it holds is what orders the writes.
     */
    public Optional<JsonNode> byLocator(String hotelId, String locator) {
        var found = ohip.get(hotelId, "/rsv/v1/hotels/{h}/reservations?externalReferenceIds={id}&externalSystemCodes={ext}",
                hotelId, locator, properties.externalSystemCode()).body().path("reservations").path("reservationInfo");
        if (!found.isArray() || found.isEmpty()) {
            return Optional.empty();
        }
        var whole = ohip.get(hotelId, "/rsv/v1/hotels/{h}/reservations/{id}", hotelId, id(found.get(0))).body()
                .path("reservations").path("reservation");
        return whole.isArray() && !whole.isEmpty() ? Optional.of(whole.get(0)) : Optional.empty();
    }

    /** The primary guest's profile of a reservation Opera has, if it names one. */
    public static Optional<String> guestProfileId(JsonNode reservation) {
        for (var guest : reservation.path("reservationGuests")) {
            var id = guest.path("profileInfo").path("profileIdList").path(0).path("id").asText(null);
            if (id != null && !id.isBlank()) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    public String create(String hotelId, JsonNode body) {
        return OperaProfiles.lastSegment(ohip.post(hotelId, "/rsv/v1/hotels/{h}/reservations", body, hotelId).location());
    }

    /**
     * A change is sent as {@code {"reservations": [ ... ]}} naming the reservation, not wrapped like a
     * creation: a real tenant answers the creation's shape with 400 «Unknown property».
     */
    public void update(String hotelId, String reservationId, JsonNode body) {
        var reservation = ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("reservations").path("reservation").get(0)).deepCopy();
        reservation.putArray("reservationIdList").addObject().put("id", reservationId).put("type", "Reservation");
        var change = objectMapper.createObjectNode();
        change.putArray("reservations").add(reservation);
        ohip.put(hotelId, "/rsv/v1/hotels/{h}/reservations/{id}", change, hotelId, reservationId);
    }

    /**
     * A real tenant wants the reason's description as well as its code: without it, 400
     * OPERAWS-RSV11046 «Cancellation reason cannot be empty».
     */
    public void cancel(String hotelId, String reservationId, String reasonCode, String description) {
        var body = objectMapper.createObjectNode();
        body.putObject("reason").put("code", reasonCode).put("description", description);
        var item = body.putArray("reservations").addObject();
        item.put("hotelId", hotelId);
        item.putArray("reservationIdList").addObject().put("id", reservationId).put("type", "Reservation");
        ohip.post(hotelId, "/rsv/v1/hotels/{h}/reservations/{id}/cancellations", body, hotelId, reservationId);
    }

    /**
     * Posts, as a deposit, a payment the central office collected — unless it is already there.
     * Each goes with the CRS's payment id as its reference, which is what makes reprojecting a
     * reservation apply a payment once (F014).
     */
    public boolean ensureDeposit(String hotelId, String reservationId, Payment payment, String methodCode, String currency) {
        var existing = ohip.get(hotelId, "/csh/v1/hotels/{h}/reservations/{id}/depositPayments", hotelId, reservationId)
                .body().path("depositPayments");
        for (var deposit : existing) {
            if (payment.paymentId().equals(deposit.path("postingReference").asText(deposit.path("reference").asText()))) {
                return false;
            }
        }
        // The shape a real tenant takes: the posting inside criteria (checked against OHIP UAT).
        ohip.post(hotelId, "/csh/v1/hotels/{h}/reservations/{id}/depositPayments", Map.of("criteria", Map.of(
                "hotelId", hotelId,
                "paymentMethod", Map.of("paymentMethod", methodCode),
                "postingAmount", Map.of("amount", payment.amount(), "currencyCode", currency),
                "postingReference", payment.paymentId(),
                "comments", "Collected by the central office: " + payment.type())), hotelId, reservationId);
        return true;
    }

    public static String id(JsonNode reservation) {
        for (var id : reservation.path("reservationIdList")) {
            if ("Reservation".equals(id.path("type").asText())) {
                return id.path("id").asText();
            }
        }
        return reservation.path("reservationIdList").path(0).path("id").asText();
    }

    public static boolean cancelled(JsonNode reservation) {
        return "Cancelled".equals(reservation.path("reservationStatus").asText());
    }

    /** The CRS version last written to this reservation, or -1 if it carries none. */
    public long writtenVersion(JsonNode reservation) {
        for (var udf : reservation.path("userDefinedFields").path("numericUDFs")) {
            if (properties.versionUdf().equals(udf.path("name").asText())) {
                return udf.path("value").asLong(-1);
            }
        }
        return -1;
    }
}
