package io.mateu.ecdemo1.pmsintegration.write;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.partner.BillingMode;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.integration.model.reservation.Room;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * A reservation of the integration, as the body OHIP's rsv takes. Every CRS code is already
 * translated — this only arranges.
 *
 * <p>What it carries, from F001 and F014: the nightly breakdown with a fixed rate, so the desk
 * cannot change the price the CRS agreed; the CRS locator and the partner's voucher as external
 * references; the guest profile and, when sold through a partner, the partner's profile, with the
 * stay routed to the partner's folio window when the partner pays (NO_FRONT); and the CRS version
 * in a UDF — the order guard; and the integration's «Custom Reference», for the desk to search by.
 *
 * <p>One simplification the PoC makes and says so: a CRS booking of several rooms is written as one
 * Opera reservation with one room rate per room. Opera's own model is a reservation per room,
 * linked; how the CRS's multi-room bookings should land there is a question for the DT.
 */
@Component
@RequiredArgsConstructor
public class ReservationPayload {

    final ObjectMapper objectMapper;
    final OhipProperties properties;

    public ObjectNode build(Reservation r, IntegrationClients.Resolved codes, String pmsHotelId, String guestProfileId,
                            Partner partner, String partnerProfileId, String partnerProfileType) {
        var body = objectMapper.createObjectNode();
        var reservation = body.putObject("reservations").putArray("reservation").addObject();
        reservation.put("hotelId", pmsHotelId);
        if (!properties.customReference().isBlank()) {
            reservation.put("customReference", properties.customReference());
        }
        var channel = codes.translation(CodeType.CHANNEL, r.channelCode());

        var stay = reservation.putObject("roomStay");
        stay.put("arrivalDate", r.arrival().toString()).put("departureDate", r.departure().toString());
        var rates = stay.putArray("roomRates");
        int adults = 0;
        int children = 0;
        for (Room room : r.rooms()) {
            adults += room.adults();
            children += room.childrenAges().size();
            var rate = rates.addObject();
            rate.put("roomType", codes.target(CodeType.ROOM_TYPE, room.roomTypeCode()))
                    .put("ratePlanCode", codes.target(CodeType.RATE_PLAN, room.ratePlanCode()))
                    .put("sourceCode", channel.targetCode())
                    .put("marketCode", channel.attribute("marketCode"))
                    .put("start", r.arrival().toString()).put("end", r.departure().toString())
                    .put("numberOfUnits", 1).put("fixedRate", true);
            var counts = rate.putObject("guestCounts").put("adults", room.adults()).put("children", room.childrenAges().size());
            var ages = counts.putArray("childAges");
            room.childrenAges().forEach(ages::add);
            var nightly = rate.putObject("rates").putArray("rate");
            var total = BigDecimal.ZERO;
            for (var night : room.nightlyRates()) {
                nightly.addObject()
                        .put("start", night.date().toString()).put("end", night.date().plusDays(1).toString())
                        .putObject("base").put("amountBeforeTax", night.amount()).put("currencyCode", r.currency());
                total = total.add(night.amount());
            }
            rate.putObject("total").put("amountBeforeTax", total);
        }
        stay.putObject("guestCounts").put("adults", adults).put("children", children);

        reservation.putArray("reservationGuests").addObject().put("primary", true)
                .putObject("profileInfo").putArray("profileIdList").addObject().put("id", guestProfileId).put("type", "Profile");

        var packages = reservation.putArray("reservationPackages");
        r.rooms().stream().map(room -> codes.target(CodeType.BOARD, room.boardCode())).distinct()
                .filter(code -> !"NONE".equals(code))
                .forEach(code -> packages.addObject().put("packageCode", code)
                        .put("startDate", r.arrival().toString()).put("endDate", r.departure().toString()));

        var method = r.payments().isEmpty() ? properties.payAtHotelMethod()
                : codes.target(CodeType.PAYMENT_METHOD, r.payments().getFirst().methodCode());
        reservation.putArray("reservationPaymentMethods").addObject().put("paymentMethod", method).put("folioView", 1);

        var references = reservation.putArray("externalReferences");
        references.addObject().put("id", r.locator()).put("idContext", properties.externalSystemCode());
        if (partner != null && r.externalReference() != null) {
            references.addObject().put("id", r.externalReference()).put("idContext", partner.code());
        }
        if (partner != null) {
            reservation.putObject("reservationProfiles").putArray("reservationProfile").addObject()
                    .put("reservationProfileType", reservationProfileType(partnerProfileType))
                    .putArray("profileIdList").addObject().put("id", partnerProfileId).put("type", "Profile");
            if (partner.billingMode() == BillingMode.NO_FRONT) {
                // The partner pays the stay: room and board go to its window; the guest's window keeps
                // what the guest consumes (F001, "ventanas de folio").
                var routing = reservation.putArray("routingInstructions").addObject();
                routing.put("folioWindowNo", 2).put("profileId", partnerProfileId);
                routing.putArray("transactionCodes").add("ROOM").add("PACKAGE");
            }
        }
        reservation.putObject("userDefinedFields").putArray("numericUDFs").addObject()
                .put("name", properties.versionUdf()).put("value", r.version());
        if (r.comments() != null && !r.comments().isBlank()) {
            reservation.putArray("comments").addObject().putObject("comment")
                    .put("commentTitle", "CRS").put("type", "GEN").put("internal", false)
                    .putObject("text").put("value", r.comments());
        }
        return body;
    }

    static String reservationProfileType(String profileType) {
        return switch (profileType) {
            case "Agent" -> "TravelAgent";
            case "Company" -> "Company";
            default -> "Source";
        };
    }
}
