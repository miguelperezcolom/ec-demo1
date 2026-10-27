package io.mateu.ecdemo1.pmsintegration.ohip;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What Opera would sell: the room types with rooms left for a stay, and how many rooms of each are
 * free night by night. The tenant has no inventory API (no house or room-type availability): the only
 * reading is the availability search, which answers which room types it would sell N rooms of for the
 * whole stay. A count is that search asked for 1, 2, 3… rooms until a type drops out — up to a cap,
 * past which it reads "cap or more". Every answer is a read; nothing is ever held or written.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OperaAvailability {

    /** The room counts asked for, a night: exact up to the last, which reads «that many or more». */
    static final int[] LEVELS = {1, 2, 3, 4, 5};
    public static final int CAP = LEVELS[LEVELS.length - 1];
    static final Duration FRESH = Duration.ofMinutes(15);

    final OhipClient ohip;
    final Connections connections;
    final Clock clock;
    final Map<String, Calendar> calendars = new ConcurrentHashMap<>();

    /** The room types Opera would sell {@code quantity} rooms of for every night from arrival to departure. */
    public Set<String> sellable(String hotelId, LocalDate arrival, LocalDate departure, int quantity) {
        JsonNode body = ohip.get(hotelId, "/par/v1/hotels/{h}/availability?roomStayStartDate={a}&roomStayEndDate={d}"
                + "&adults=1&roomStayQuantity={q}", hotelId, arrival, departure, quantity).body();
        var types = new TreeSet<String>();
        for (var hotel : body == null ? List.<JsonNode>of() : iterable(body.path("hotelAvailability"))) {
            for (var stay : hotel.path("roomStays")) {
                for (var rate : stay.path("roomRates")) {
                    var type = rate.path("roomType").asText("");
                    if (!type.isBlank()) {
                        types.add(type);
                    }
                }
            }
        }
        return types;
    }

    /**
     * Whether Opera would sell these rooms for the stay: {@code rooms} counts each room type once per
     * room wanted (two doubles: the type twice).
     */
    public boolean fits(String hotelId, LocalDate arrival, LocalDate departure, List<String> rooms) {
        var wanted = new TreeMap<String, Integer>();
        rooms.forEach(t -> wanted.merge(t, 1, Integer::sum));
        for (var entry : wanted.entrySet()) {
            if (!sellable(hotelId, arrival, departure, entry.getValue()).contains(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    /** One night: the free rooms of each room type Opera sells, {@link #CAP} meaning «that many or more». */
    public record Night(LocalDate date, Map<String, Integer> free) {
    }

    /** The nights from {@code from}, as read at {@code readAt}. */
    public record Calendar(String hotelId, LocalDate from, int cap, List<Night> nights, Instant readAt) {
    }

    /**
     * The free rooms of each type for the next {@code days} nights from today — read once every ten
     * minutes at most: a reading is a few searches a night.
     */
    public Calendar calendar(String hotelId, int days) {
        var from = LocalDate.now(clock);
        var key = hotelId + "|" + from + "|" + days;
        var cached = calendars.get(key);
        if (cached != null && cached.readAt().plus(FRESH).isAfter(clock.instant())) {
            return cached;
        }
        var nights = new ArrayList<Night>();
        for (var night = from; night.isBefore(from.plusDays(days)); night = night.plusDays(1)) {
            nights.add(new Night(night, free(hotelId, night)));
        }
        var fresh = new Calendar(hotelId, from, CAP, List.copyOf(nights), clock.instant());
        calendars.keySet().removeIf(k -> k.startsWith(hotelId + "|") && !k.equals(key));
        calendars.put(key, fresh);
        return fresh;
    }

    /**
     * Keeps the calendar of every property in service read: a page that shows it never waits for the
     * few dozen searches a reading takes.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${pms-integration.calendar-refresh:9m}",
            initialDelayString = "${pms-integration.calendar-first-read:45s}")
    public void keepRead() {
        for (var hotelId : connections.integrations().stream()
                .filter(i -> i.status() != io.mateu.ecdemo1.integration.model.integration.IntegrationStatus.DECOMMISSIONED)
                .map(i -> i.pmsHotelCode()).filter(java.util.Objects::nonNull).distinct().toList()) {
            try {
                calendars.remove(hotelId + "|" + LocalDate.now(clock) + "|" + DAYS);
                calendar(hotelId, DAYS);
            } catch (RuntimeException e) {
                log.debug("Opera's free rooms of {} not read: {}", hotelId, e.getMessage());
            }
        }
    }

    /** The nights the calendar covers by default (the data console's home). */
    public static final int DAYS = 14;

    /** One night's free rooms per type, asking for more rooms until each type drops out. */
    Map<String, Integer> free(String hotelId, LocalDate night) {
        var free = new LinkedHashMap<String, Integer>();
        for (var level : LEVELS) {
            var types = sellable(hotelId, night, night.plusDays(1), level);
            if (types.isEmpty()) {
                break;
            }
            types.forEach(t -> free.put(t, level));
        }
        return new TreeMap<>(free);
    }

    static Iterable<JsonNode> iterable(JsonNode array) {
        var list = new ArrayList<JsonNode>();
        array.forEach(list::add);
        return list;
    }
}
