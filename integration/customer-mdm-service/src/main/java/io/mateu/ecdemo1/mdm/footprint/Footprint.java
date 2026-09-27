package io.mateu.ecdemo1.mdm.footprint;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.Source;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Where a customer is, read from the systems that hold its reservations: the CRS's bookings the
 * lineage names, and the front office's stays. Read-only and never failing its caller: a system
 * that does not answer leaves its part of the picture empty, and the screen says so.
 */
@Component
@Slf4j
public class Footprint {

    /** A CRS booking, as the CRS holds it now. */
    public record Booking(String id, String hotelCode, LocalDate arrival, LocalDate departure, String status,
                          String holder, String pmsReservationId) {
    }

    /** A stay in the front office, and whether the customer is its guest or rooms with them. */
    public record Stay(String id, String role, LocalDate checkIn, LocalDate checkOut, String room, String status) {
    }

    /**
     * A reservation of the CRS the customer is in, from the lineage: the holder is passenger 0, the
     * room's guests follow. {@code booking} is null when the CRS did not answer for it.
     */
    public record Reservation(String hotelCode, String locator, boolean holder, boolean guest, Booking booking) {

        public String role() {
            return holder && guest ? "Titular y huésped" : holder ? "Titular" : "Huésped";
        }
    }

    final CustomerRepository customers;
    final SourceRepository sources;
    final RestClient booking;
    final RestClient frontOffice;

    public Footprint(CustomerRepository customers, SourceRepository sources, LinksProperties links) {
        this.customers = customers;
        this.sources = sources;
        this.booking = client(links.bookingUrl(), links);
        this.frontOffice = client(links.frontOfficeUrl(), links);
    }

    static RestClient client(String url, LinksProperties links) {
        if (url == null || url.isBlank()) {
            return null;
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(links.timeout());
        factory.setReadTimeout(links.timeout());
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public boolean readsBookings() {
        return booking != null;
    }

    public boolean readsFrontOffice() {
        return frontOffice != null;
    }

    /** The CRS bookings the customer is in — its own and those of the codes merged into it — latest arrival first. */
    public List<Reservation> reservations(Customer customer) {
        var byReservation = new LinkedHashMap<String, List<Source>>();
        for (var id : codesOf(customer)) {
            for (var s : sources.findByCustomerIdOrderByFirstSeenAsc(id)) {
                byReservation.computeIfAbsent(s.hotelCode + "/" + s.locator, k -> new ArrayList<>()).add(s);
            }
        }
        var reservations = byReservation.values().stream().map(passengers -> {
            var first = passengers.get(0);
            var holder = passengers.stream().anyMatch(s -> s.passenger == 0);
            var guest = passengers.stream().anyMatch(s -> s.passenger > 0);
            return new Reservation(first.hotelCode, first.locator, holder, guest, booking(first.locator).orElse(null));
        }).toList();
        return reservations.stream()
                .sorted(Comparator.comparing((Reservation r) -> r.booking() == null || r.booking().arrival() == null
                        ? LocalDate.MIN : r.booking().arrival()).reversed())
                .toList();
    }

    /** The customer's code and every code a merge folded into it: a reservation may name any of them. */
    public List<String> codesOf(Customer customer) {
        var codes = new TreeSet<String>();
        var pending = new ArrayList<>(List.of(customer.id));
        while (!pending.isEmpty() && codes.size() < 200) {
            var id = pending.remove(pending.size() - 1);
            if (codes.add(id)) {
                customers.findByAliasOf(id).forEach(a -> pending.add(a.id));
            }
        }
        var ordered = new ArrayList<String>();
        ordered.add(customer.id);
        codes.stream().filter(c -> !c.equals(customer.id)).forEach(ordered::add);
        return ordered;
    }

    /** A CRS booking by its id — the locator the integration and the lineage know it by. */
    public Optional<Booking> booking(String id) {
        if (booking == null || id == null) {
            return Optional.empty();
        }
        try {
            var b = booking.get().uri("/bookings/{id}", id).retrieve().body(JsonNode.class);
            if (b == null) {
                return Optional.empty();
            }
            var holder = b.path("holder");
            return Optional.of(new Booking(text(b, "id"), text(b, "hotelCode"), date(b, "arrival"), date(b, "departure"),
                    text(b, "status"),
                    ((holder.path("firstName").asText("") + " " + holder.path("lastName").asText("")).trim()),
                    b.path("pmsReference").isObject() ? text(b.path("pmsReference"), "reservationId") : null));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("The CRS did not answer for booking {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * The front office's stays of a customer — as the guest or in the room — or empty when it does
     * not answer. The front office knows a guest by its MDM code, so each code merged into the
     * customer is asked too.
     */
    public List<Stay> stays(Customer customer) {
        if (frontOffice == null) {
            return List.of();
        }
        var stays = new LinkedHashMap<String, Stay>();
        for (var code : codesOf(customer)) {
            try {
                var answer = frontOffice.get().uri("/api/guests/{id}/stays", code).retrieve().body(JsonNode.class);
                if (answer != null) {
                    answer.forEach(s -> stays.putIfAbsent(text(s, "id"), new Stay(text(s, "id"),
                            "HOLDER".equals(text(s, "role")) ? "Huésped" : "Acompañante",
                            date(s, "checkIn"), date(s, "checkOut"), text(s, "room"), text(s, "status"))));
                }
            } catch (RuntimeException e) {
                log.warn("The front office did not answer for {}: {}", code, e.getMessage());
            }
        }
        return stays.values().stream()
                .sorted(Comparator.comparing((Stay s) -> s.checkIn() == null ? LocalDate.MIN : s.checkIn()).reversed())
                .toList();
    }

    /** The status of the front office's stay for a CRS booking, if it has one. */
    public Optional<String> stayStatus(String locator) {
        if (frontOffice == null || locator == null) {
            return Optional.empty();
        }
        try {
            var stay = frontOffice.get().uri("/api/reservations/{locator}", locator).retrieve().body(JsonNode.class);
            return Optional.ofNullable(stay == null ? null : text(stay, "status"));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("The front office did not answer for stay {}: {}", locator, e.getMessage());
            return Optional.empty();
        }
    }

    static String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    static LocalDate date(JsonNode node, String field) {
        var value = text(node, field);
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
