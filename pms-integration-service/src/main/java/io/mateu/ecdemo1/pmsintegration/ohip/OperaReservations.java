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

    /** The reservation the CRS knows by this locator, if Opera has it. */
    public Optional<JsonNode> byLocator(String hotelId, String locator) {
        var found = ohip.get(hotelId, "/rsv/v1/hotels/{h}/reservations?externalReferenceIds={id}&externalSystemCodes={ext}",
                hotelId, locator, properties.externalSystemCode()).body().path("reservations").path("reservation");
        return found.isArray() && !found.isEmpty() ? Optional.of(found.get(0)) : Optional.empty();
    }

    public String create(String hotelId, JsonNode body) {
        return OperaProfiles.lastSegment(ohip.post(hotelId, "/rsv/v1/hotels/{h}/reservations", body, hotelId).location());
    }

    public void update(String hotelId, String reservationId, JsonNode body) {
        ohip.put(hotelId, "/rsv/v1/hotels/{h}/reservations/{id}", body, hotelId, reservationId);
    }

    public void cancel(String hotelId, String reservationId, String reasonCode) {
        var body = objectMapper.createObjectNode();
        body.putObject("reason").put("code", reasonCode);
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
            if (payment.paymentId().equals(deposit.path("reference").asText())) {
                return false;
            }
        }
        ohip.post(hotelId, "/csh/v1/hotels/{h}/reservations/{id}/depositPayments", Map.of(
                "paymentMethod", Map.of("paymentMethod", methodCode),
                "amount", Map.of("amount", payment.amount(), "currencyCode", currency),
                "reference", payment.paymentId(),
                "comments", "Collected by the central office: " + payment.type()), hotelId, reservationId);
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
