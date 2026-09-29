package io.mateu.ecdemo1.journey.application;

import io.mateu.ecdemo1.journey.JourneyProperties;
import io.mateu.ecdemo1.journey.business.BusinessData;
import io.mateu.ecdemo1.journey.business.BusinessLookup;
import io.mateu.ecdemo1.journey.model.Journey;
import io.mateu.ecdemo1.journey.model.JourneyMapper;
import io.mateu.ecdemo1.journey.tempo.TempoClient;
import io.mateu.ecdemo1.journey.tempo.TraceParser;
import io.mateu.ecdemo1.journey.tempo.TraceSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A booking's journeys: its traces found in Tempo by locator, each read and told as a {@link
 * Journey}, newest first, with what the systems say about the booking now. Kept for a few seconds,
 * because one screen asks for its parts one by one.
 */
@Service
@Slf4j
public class Journeys {

    final TempoClient tempo;
    final BusinessLookup business;
    final JourneyProperties properties;
    final Clock clock = Clock.systemUTC();
    final Map<String, BookingJourney> cache = new ConcurrentHashMap<>();

    public Journeys(TempoClient tempo, BusinessLookup business, JourneyProperties properties) {
        this.tempo = tempo;
        this.business = business;
        this.properties = properties;
    }

    public BookingJourney of(String locator) {
        var key = locator.trim();
        var cached = cache.get(key);
        if (cached != null && cached.readAt().plus(properties.cacheFor()).isAfter(Instant.now(clock))) {
            return cached;
        }
        var fresh = read(key);
        cache.put(key, fresh);
        if (cache.size() > 200) {
            cache.clear();
        }
        return fresh;
    }

    public void forget(String locator) {
        cache.remove(locator.trim());
    }

    BookingJourney read(String locator) {
        List<TraceSummary> traces;
        try {
            traces = tempo.tracesOf(locator);
        } catch (RuntimeException e) {
            log.warn("Tempo did not answer for {}: {}", locator, e.getMessage());
            return new BookingJourney(locator, null, List.of(), BusinessData.empty(), 0,
                    "No se ha podido leer Tempo: " + e.getMessage(), Instant.now(clock));
        }
        var changes = new ArrayList<Journey>();
        var unread = 0;
        var now = Instant.now(clock);
        var nowNanos = now.getEpochSecond() * 1_000_000_000L + now.getNano();
        for (var trace : traces) {
            var json = tempo.trace(trace.traceId());
            if (json.isEmpty()) {
                unread++;
                continue;
            }
            var spans = TraceParser.parse(json.get());
            if (spans.isEmpty()) {
                unread++;
                continue;
            }
            changes.add(JourneyMapper.map(trace.traceId(), locator, spans, nowNanos));
        }
        changes.sort(Comparator.comparingLong(Journey::startNanos).reversed());
        var hotel = changes.stream().map(Journey::hotel).filter(h -> h != null).findFirst().orElse(null);
        var data = business.of(hotel, locator);
        if (hotel == null && data.booking() != null) {
            hotel = data.booking().hotelCode();
        }
        return new BookingJourney(locator, hotel, List.copyOf(changes), data, unread, null, now);
    }

    /** The bookings with traces lately, newest change first; {@code text} narrows by locator. */
    public List<RecentBooking> recent(String text) {
        var byLocator = new LinkedHashMap<String, List<TraceSummary>>();
        for (var trace : tempo.recent(text, 200)) {
            var locator = trace.matched().get("booking.locator");
            if (locator != null && !locator.isBlank()) {
                byLocator.computeIfAbsent(locator, k -> new ArrayList<>()).add(trace);
            }
        }
        var list = new ArrayList<RecentBooking>();
        byLocator.forEach((locator, traces) -> {
            var last = traces.get(0);
            var hotel = traces.stream().map(t -> t.matched().get("hotel.code")).filter(h -> h != null).findFirst().orElse("");
            list.add(new RecentBooking(locator, hotel, last.start(), traces.size(), kindOf(last), last.durationMillis()));
        });
        list.sort(Comparator.comparing(RecentBooking::lastChange).reversed());
        return list;
    }

    /** What a trace was, from its root alone — the list does not read every trace. */
    static String kindOf(TraceSummary trace) {
        var event = io.mateu.ecdemo1.journey.model.Kind.ofEvent(trace.matched().get("booking.event"));
        if (event == io.mateu.ecdemo1.journey.model.Kind.CHECK_IN || event == io.mateu.ecdemo1.journey.model.Kind.CHECK_OUT
                || event == io.mateu.ecdemo1.journey.model.Kind.CHARGE || event == io.mateu.ecdemo1.journey.model.Kind.CHARGE_VOID) {
            return event.label();
        }
        var name = trace.rootName();
        if (name.endsWith("/cancel")) {
            return "Cancelada";
        }
        if (name.equals("http post /bookings")) {
            return "Creada";
        }
        if (name.startsWith("http put /bookings")) {
            return "Modificada";
        }
        if (name.endsWith("/no-shows") || event == io.mateu.ecdemo1.journey.model.Kind.NO_SHOW) {
            return "No-show";
        }
        if (name.endsWith("/walk-ins") || "front-office".equals(trace.rootService())) {
            return "Walk-in";
        }
        if (name.startsWith("projection-requests")) {
            return "Reproyectada";
        }
        if (trace.rootService().contains("mapping") || trace.rootService().contains("integrations")) {
            return "Reanudada";
        }
        return "Cambio";
    }
}
