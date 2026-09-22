package io.mateu.ecdemo1.pmsintegration.ohip;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A property's configuration in Opera, as the integration's code entries — the other half of what
 * the mapping pairs. Read from rmcfg, rtp, rsvcfg and lov; profile types are the product's own.
 */
@Component
@RequiredArgsConstructor
public class OperaCatalog {

    final OhipClient ohip;
    final Connections connections;

    /**
     * The Opera properties the integrations name and, when one is named, that property's codes. A board with no
     * package in Opera — room only — is the code NONE.
     */
    public List<CodeEntry> catalog(String hotelId) {
        var entries = new ArrayList<CodeEntry>();
        connections.integrations().stream()
                .filter(i -> i.status() != IntegrationStatus.DECOMMISSIONED)
                .forEach(i -> entries.add(new CodeEntry(CodeType.HOTEL, null, i.pmsHotelCode(),
                        "Opera property " + i.pmsHotelCode() + " (CRS " + i.crsHotelCode() + ")")));
        for (var type : List.of("Agent", "Company", "Source")) {
            entries.add(new CodeEntry(CodeType.PARTNER_TYPE, null, type, "Opera profile type " + type));
        }
        if (hotelId == null || hotelId.isBlank()) {
            return entries;
        }
        for (var group : ohip.get(hotelId, "/rm/config/v1/hotels/{h}/roomTypes", hotelId).body().path("roomTypes")) {
            for (var r : group.path("roomType")) {
                entries.add(entry(CodeType.ROOM_TYPE, hotelId, r.path("roomType").asText(), text(r.path("shortDescription"))));
            }
        }
        for (var r : ohip.get(hotelId, "/rtp/v1/hotels/{h}/ratePlans", hotelId).body().path("ratePlans")) {
            entries.add(entry(CodeType.RATE_PLAN, hotelId, r.path("ratePlanCode").asText(),
                    text(r.path("primaryDetails").path("description"))));
        }
        entries.add(entry(CodeType.BOARD, hotelId, "NONE", "No package: room only"));
        for (var r : ohip.get(hotelId, "/rtp/v1/packages?hotelIds={h}", hotelId).body().path("packages")) {
            entries.add(entry(CodeType.BOARD, hotelId, r.path("packageCode").asText(), text(r.path("primaryDetails").path("description"))));
        }
        for (var r : ohip.get(hotelId, "/rsv/config/v1/hotels/{h}/sourceCodes/", hotelId).body().path("sourceCodes")) {
            entries.add(entry(CodeType.CHANNEL, hotelId, r.path("code").asText(), "Source code: " + text(r.path("description"))));
        }
        for (var r : ohip.get(hotelId, "/rsv/config/v1/marketCodes?hotelIds={h}", hotelId).body().path("marketCodes")) {
            entries.add(entry(CodeType.MARKET, hotelId, r.path("code").asText(), "Market code: " + text(r.path("description"))));
        }
        for (var r : ohip.get(hotelId, "/lov/v1/listOfValues/hotels/{h}/paymentMethods", hotelId).body().path("listOfValues").path("items")) {
            entries.add(entry(CodeType.PAYMENT_METHOD, hotelId, r.path("code").asText(), r.path("description").asText()));
        }
        for (var r : ohip.get(hotelId, "/rsv/config/v1/cancellationCodes?hotelIds={h}", hotelId).body().path("cancellationCodes")) {
            entries.add(entry(CodeType.CANCELLATION_REASON, hotelId, r.path("code").asText(), text(r.path("description"))));
        }
        return entries;
    }

    static CodeEntry entry(CodeType type, String hotelId, String code, String description) {
        return new CodeEntry(type, hotelId, code, description);
    }

    /** A description in OPERA is either plain text or a translatable object with a default text. */
    static String text(JsonNode node) {
        return node.isTextual() ? node.asText() : node.path("defaultText").asText();
    }
}
