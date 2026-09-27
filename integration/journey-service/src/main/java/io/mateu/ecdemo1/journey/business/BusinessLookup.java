package io.mateu.ecdemo1.journey.business;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.journey.JourneyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Asks the systems that own them for what the spans do not carry: the CRS for the booking (its
 * Opera reference, its codes), the MDM for its people, their Salesforce contacts, their Opera
 * profiles and the stay in the front office, and the mapping for the causes it waited on and the
 * equivalences of its codes. Short timeouts, and nothing here is required: the journey is drawn
 * from the trace, this only names things in it.
 */
@Component
@Slf4j
public class BusinessLookup {

    final RestClient booking;
    final RestClient mdm;
    final RestClient mapping;

    public BusinessLookup(JourneyProperties properties) {
        this.booking = client(properties.bookingUrl());
        this.mdm = client(properties.mdmUrl());
        this.mapping = client(properties.mappingUrl());
    }

    static RestClient client(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public BusinessData of(String hotelCode, String locator) {
        var unavailable = new ArrayList<String>();
        var bookingNode = get(booking, "CRS", unavailable, "/bookings/{id}", locator);
        var hotel = hotelCode != null ? hotelCode : text(bookingNode, "hotelCode");
        BusinessData.Booking crs = null;
        if (bookingNode != null) {
            var holder = bookingNode.path("holder");
            crs = new BusinessData.Booking(text(bookingNode, "hotelCode"), text(bookingNode, "status"),
                    bookingNode.path("version").asLong(), text(bookingNode, "channelCode"),
                    (text(holder, "firstName") + " " + text(holder, "lastName")).replace("null", "").trim(),
                    text(bookingNode, "arrival"), text(bookingNode, "departure"),
                    text(bookingNode.path("pmsReference"), "reservationId"),
                    text(bookingNode.path("cancellation"), "reasonCode"));
        }

        var passengers = new ArrayList<BusinessData.Passenger>();
        var profiles = new ArrayList<String>();
        BusinessData.Stay stay = null;
        var links = hotel == null ? null : get(mdm, "MDM", unavailable, "/reservations/{hotel}/{locator}/links", hotel, locator);
        if (links != null) {
            var seen = new java.util.HashSet<String>();
            for (var p : links.path("passengers")) {
                // The holder is usually also a guest of a room: one person, said once.
                if (text(p, "customerId") != null && !seen.add(text(p, "customerId"))) {
                    continue;
                }
                passengers.add(new BusinessData.Passenger(p.path("passenger").asInt(), text(p, "role"), text(p, "customerId"),
                        text(p, "name"), text(p, "status"), text(p, "customerRoute"), text(p, "salesforceContactId"),
                        text(p, "salesforceContactUrl")));
            }
            for (var o : links.path("operaProfiles")) {
                profiles.add(text(o, "profileId"));
            }
            var fo = links.path("frontOffice");
            if (fo.isObject()) {
                stay = new BusinessData.Stay(text(fo, "status"), text(fo, "url"));
            }
        }

        var codes = codes(bookingNode);
        var causes = new ArrayList<BusinessData.CauseInfo>();
        var all = get(mapping, "Mapeado", unavailable, "/causes?openOnly=false");
        if (all != null) {
            for (var c : all) {
                var key = text(c, "key");
                if (key != null && concerns(key, hotel, locator, codes)) {
                    causes.add(new BusinessData.CauseInfo(key, text(c, "type"), text(c, "description"), text(c, "status"),
                            text(c, "openedAt"), text(c, "resolvedAt"), text(c, "resolvedBy")));
                }
            }
        }
        var translations = new ArrayList<String>();
        if (mapping != null && hotel != null && !codes.isEmpty()) {
            try {
                var body = Map.of("hotelCode", hotel, "codes", codes.stream()
                        .filter(c -> !c.startsWith("PARTNER "))
                        .map(c -> Map.of("type", c.substring(0, c.indexOf(' ')), "code", c.substring(c.indexOf(' ') + 1))).toList());
                var resolved = mapping.post().uri("/resolve").body(body).retrieve().body(JsonNode.class);
                if (resolved != null) {
                    for (var t : resolved.path("translations")) {
                        translations.add(text(t, "type") + " " + text(t, "sourceCode") + "=" + text(t, "targetCode"));
                    }
                }
            } catch (RuntimeException e) {
                log.debug("The mapping did not translate {}'s codes: {}", locator, e.getMessage());
            }
        }
        return new BusinessData(crs, List.copyOf(passengers), List.copyOf(profiles), stay, List.copyOf(causes),
                List.copyOf(translations), List.copyOf(unavailable));
    }

    /** The booking's codes as "TYPE CODE", the way the mapping keys its causes. */
    static List<String> codes(JsonNode booking) {
        var codes = new LinkedHashSet<String>();
        if (booking == null) {
            return List.of();
        }
        add(codes, "HOTEL", text(booking, "hotelCode"));
        add(codes, "CHANNEL", text(booking, "channelCode"));
        for (var room : booking.path("rooms")) {
            add(codes, "ROOM_TYPE", text(room, "roomTypeCode"));
            add(codes, "RATE_PLAN", text(room, "ratePlanCode"));
            add(codes, "BOARD", text(room, "boardCode"));
        }
        for (var payment : booking.path("payments")) {
            add(codes, "PAYMENT_METHOD", text(payment, "methodCode"));
        }
        add(codes, "CANCELLATION_REASON", text(booking.path("cancellation"), "reasonCode"));
        add(codes, "PARTNER", text(booking, "partnerCode"));
        return List.copyOf(codes);
    }

    static void add(LinkedHashSet<String> codes, String type, String code) {
        if (code != null && !code.isBlank()) {
            codes.add(type + " " + code);
        }
    }

    /**
     * Whether a cause is this booking's: one that names it (Opera refused it, it is not in the PMS
     * yet), a missing equivalence for one of its codes, or its hotel's integration not being active.
     */
    static boolean concerns(String key, String hotel, String locator, List<String> codes) {
        if (key.contains(":" + locator + ":") || key.endsWith(":" + locator)) {
            return true;
        }
        if (key.startsWith("INTEGRATION_INACTIVE") && hotel != null && key.endsWith(":" + hotel)) {
            return true;
        }
        if (key.startsWith("MISSING_PARTNER:")) {
            return codes.contains("PARTNER " + key.substring("MISSING_PARTNER:".length()));
        }
        if (key.startsWith("MISSING_MAPPING:")) {
            var parts = key.split(":");
            if (parts.length >= 4) {
                var scope = parts[1];
                var code = parts[2] + " " + String.join(":", java.util.Arrays.copyOfRange(parts, 3, parts.length));
                return ("chain".equals(scope) || scope.equals(hotel)) && codes.contains(code);
            }
        }
        return false;
    }

    JsonNode get(RestClient client, String system, List<String> unavailable, String uri, Object... variables) {
        if (client == null) {
            return null;
        }
        try {
            return client.get().uri(uri, variables).retrieve().body(JsonNode.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return null;
        } catch (RuntimeException e) {
            log.info("{} did not answer {}: {}", system, uri, e.getMessage());
            unavailable.add(system);
            return null;
        }
    }

    static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
