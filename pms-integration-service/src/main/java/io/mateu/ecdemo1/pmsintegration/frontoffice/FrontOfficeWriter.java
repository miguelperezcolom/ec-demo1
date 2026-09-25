package io.mateu.ecdemo1.pmsintegration.frontoffice;

import io.mateu.ecdemo1.integration.model.customer.IdentityRequest;
import io.mateu.ecdemo1.integration.model.customer.ResolvedIdentity;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients.CodeRef;
import io.mateu.ecdemo1.pmsintegration.config.PmsIntegrationProperties;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaCatalog;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hotel's front office, the other system at the property besides the PMS: every reservation
 * written into Opera is written into it too, as a stay to arrive, and a cancelled one cancels its
 * stay. The front office reads the PMS's words — "Suite Junior Standard Balcón", not the CRS's JSU —
 * so the codes go through the mapping and come out as Opera's catalogue describes them.
 *
 * <p>Only for the CRS hotels that have one ({@code front-office-hotels}); for the rest the steps do
 * nothing. A front office that does not answer is retried by the engine; one that refuses to cancel
 * a guest already in the house has decided — that is the desk's, and the step ends.
 */
@Component
@Slf4j
public class FrontOfficeWriter {

    static final Duration CATALOGUE_FOR = Duration.ofMinutes(30);

    record Catalogue(Instant readAt, Map<String, String> descriptions) {
    }

    final PmsIntegrationProperties properties;
    final IntegrationClients integration;
    final OperaCatalog catalog;
    final Clock clock;
    final RestClient frontOffice;
    final Map<String, Catalogue> catalogues = new ConcurrentHashMap<>();

    public FrontOfficeWriter(PmsIntegrationProperties properties, IntegrationClients integration, OperaCatalog catalog,
                             TolerantReader reader, Clock clock) {
        this.properties = properties;
        this.integration = integration;
        this.catalog = catalog;
        this.clock = clock;
        this.frontOffice = RestClient.builder()
                .baseUrl(properties.frontOfficeUrl() == null ? "http://front-office" : properties.frontOfficeUrl())
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.addFirst(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
    }

    boolean hasFrontOffice(String crsHotelCode) {
        return properties.frontOfficeUrl() != null && !properties.frontOfficeUrl().isBlank()
                && properties.frontOfficeHotels() != null && properties.frontOfficeHotels().contains(crsHotelCode);
    }


    /**
     * A version Opera already held was written into the front office when it was written into Opera.
     * Only a backfill — which fills the front office with what Opera had before it existed — and a
     * merge in the MDM — a new customer code for the holder — write it again.
     */
    public static boolean alreadyWritten(TaskExecutionRequested task) {
        var outcome = task.variables().stream().filter(v -> ProcessVariables.WRITE_OUTCOME.equals(v.name()))
                .map(Variable::value).findFirst().orElse("");
        var origin = task.variables().stream().filter(v -> ProcessVariables.ORIGIN.equals(v.name()))
                .map(Variable::value).filter(v -> v != null).findFirst().orElse("");
        return "STALE".equals(outcome) && !origin.startsWith("backfill") && !origin.startsWith("mdm-");
    }
    public List<Variable> write(TaskExecutionRequested task) {
        var hotelCode = var(task, ProcessVariables.HOTEL_CODE);
        if (!hasFrontOffice(hotelCode)) {
            return List.of();
        }
        if (alreadyWritten(task)) {
            log.info("{}: Opera already held this version; the front office is not written again", var(task, ProcessVariables.LOCATOR));
            return List.of();
        }
        var r = integration.reservation(hotelCode, var(task, ProcessVariables.LOCATOR));
        var resolved = integration.resolve(r.hotelCode(), codes(r));
        var pmsHotel = resolved.target(CodeType.HOTEL, r.hotelCode());
        var words = descriptions(pmsHotel);
        var room = r.rooms().getFirst();
        var roomType = describe(words, CodeType.ROOM_TYPE, resolved.target(CodeType.ROOM_TYPE, room.roomTypeCode()));
        var boardCode = resolved.target(CodeType.BOARD, room.boardCode());
        var board = "NONE".equals(boardCode) ? "Solo alojamiento" : describe(words, CodeType.BOARD, boardCode);
        var pax = r.rooms().stream().mapToInt(x -> x.adults() + x.childrenAges().size()).sum();
        var agency = r.partnerCode() == null ? "Directo · " + r.channelCode() : integration.partner(r.partnerCode()).name();
        var holder = r.holder();
        var body = new HashMap<String, Object>();
        var customerId = customerOf(task, r);
        var master = customerId == null ? holder : integration.customer(customerId)
                .map(m -> io.mateu.ecdemo1.pmsintegration.worker.TaskHandlers.overlay(m, holder)).orElse(holder);
        body.put("holder", person(customerId, master));
        body.put("companions", companions(r));
        body.put("roomType", roomType);
        body.put("board", board);
        body.put("checkIn", r.arrival().toString());
        body.put("checkOut", r.departure().toString());
        body.put("pax", pax);
        body.put("agency", agency);
        body.put("total", r.totalAmount());
        var written = frontOffice.put().uri("/api/reservations/{locator}", r.locator()).body(body).retrieve()
                .body(Map.class);
        log.info("{} written into the front office: stay {}", r.locator(), written == null ? "?" : written.get("status"));
        if (customerId != null) {
            integration.xref(customerId, "FRONT_OFFICE", customerId, hotelCode);
        }
        return List.of();
    }

    public List<Variable> cancel(TaskExecutionRequested task) {
        var hotelCode = var(task, ProcessVariables.HOTEL_CODE);
        if (!hasFrontOffice(hotelCode)) {
            return List.of();
        }
        var locator = var(task, ProcessVariables.LOCATOR);
        try {
            // Why, and what it still costs: a no-show is a stay the guest owes its fee for.
            var r = integration.reservation(hotelCode, locator);
            var body = new HashMap<String, Object>();
            body.put("noShow", r.noShow());
            body.put("reasonCode", r.cancellationReasonCode());
            body.put("total", r.totalAmount());
            frontOffice.post().uri("/api/reservations/{locator}/cancellation", locator).body(body).retrieve().toBodilessEntity();
            log.info("{} {} in the front office{}", locator, r.noShow() ? "a no-show" : "cancelled",
                    r.noShow() ? ", costing " + r.totalAmount() : "");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("{} was never in the front office: nothing to cancel", locator);
            } else if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.warn("{}: the front office keeps its stay — {}", locator, e.getResponseBodyAsString());
            } else {
                throw e;
            }
        }
        return List.of();
    }

    List<CodeRef> codes(Reservation r) {
        var codes = new ArrayList<CodeRef>();
        codes.add(new CodeRef(CodeType.HOTEL, r.hotelCode()));
        r.rooms().forEach(room -> {
            codes.add(new CodeRef(CodeType.ROOM_TYPE, room.roomTypeCode()));
            codes.add(new CodeRef(CodeType.BOARD, room.boardCode()));
        });
        return codes;
    }

    /** The holder's customer code: what the guest profile step stamped, or the MDM asked again. */
    String customerOf(TaskExecutionRequested task, Reservation r) {
        var known = task.variables().stream().filter(v -> ProcessVariables.CUSTOMER_ID.equals(v.name())).map(Variable::value)
                .filter(v -> v != null && !v.isBlank()).findFirst();
        if (known.isPresent()) {
            return known.get();
        }
        try {
            var passengers = new ArrayList<Person>();
            passengers.add(r.holder());
            r.rooms().forEach(room -> passengers.addAll(room.guests() == null ? List.of() : room.guests()));
            return integration.identities(new IdentityRequest(r.hotelCode(), r.locator(), passengers)).stream()
                    .filter(i -> i.passenger() == 0).map(ResolvedIdentity::customerId).findFirst().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Map<String, Object> person(String customerId, Person p) {
        var person = new HashMap<String, Object>();
        person.put("customerId", customerId);
        person.put("name", ((p.firstName() == null ? "" : p.firstName()) + " " + (p.lastName() == null ? "" : p.lastName())).trim());
        person.put("document", p.documentNumber());
        person.put("email", p.email());
        person.put("phone", p.phone());
        return person;
    }

    /** The room's other people: its guests but the holder, who usually is one of them. */
    static List<Map<String, Object>> companions(Reservation r) {
        var holder = name(r.holder());
        var result = new ArrayList<Map<String, Object>>();
        var holderSeen = false;
        for (var room : r.rooms()) {
            for (var guest : room.guests() == null ? List.<Person>of() : room.guests()) {
                if (!holderSeen && name(guest).equalsIgnoreCase(holder)) {
                    holderSeen = true;
                    continue;
                }
                result.add(person(null, guest));
            }
        }
        return result;
    }

    static String name(Person p) {
        return ((p.firstName() == null ? "" : p.firstName()) + " " + (p.lastName() == null ? "" : p.lastName())).trim();
    }

    String describe(Map<String, String> words, CodeType type, String code) {
        return words.getOrDefault(type + ":" + code, code);
    }

    /** Opera's catalogue of the property, as words; read again every half hour. */
    Map<String, String> descriptions(String pmsHotel) {
        var cached = catalogues.get(pmsHotel);
        if (cached == null || cached.readAt().plus(CATALOGUE_FOR).isBefore(clock.instant())) {
            var words = new HashMap<String, String>();
            for (CodeEntry e : catalog.catalog(pmsHotel)) {
                if (e.description() != null && !e.description().isBlank()) {
                    words.put(e.type() + ":" + e.code(), e.description());
                }
            }
            cached = new Catalogue(clock.instant(), words);
            catalogues.put(pmsHotel, cached);
        }
        return cached.descriptions();
    }

    static String var(TaskExecutionRequested task, String name) {
        return task.variables().stream().filter(v -> name.equals(v.name())).map(Variable::value)
                .filter(v -> v != null && !v.isBlank()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Step %s needs the variable %s".formatted(task.stepId(), name)));
    }
}
