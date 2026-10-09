package io.mateu.ecdemo1.customerhistory.application;

import io.mateu.ecdemo1.customerhistory.store.CustomerAlias;
import io.mateu.ecdemo1.customerhistory.store.CustomerAliasRepository;
import io.mateu.ecdemo1.customerhistory.store.CustomerStay;
import io.mateu.ecdemo1.customerhistory.store.CustomerStayRepository;
import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.ChargeKind;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.StayClosed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Every customer's stays in the chain, by the MDM's customer code ({@code C-…}): taken from the stays
 * the front office closes (one row per customer of the chain on the stay), and read through the MDM's
 * merges — a code that was absorbed into another counts as the survivor, without rewriting a row: the
 * aliases are followed when the history is read, so a merge that arrives late (or before the stays it
 * concerns) changes nothing but one alias row.
 */
@Service
@Slf4j
public class CustomerHistory {

    /** The inbox's consumer names: the topics, as each consumer reads one. */
    public static final String FRONT_OFFICE_EVENTS = FrontOfficeEvent.TOPIC;
    public static final String CUSTOMERS = "customers";

    /** How far an alias chain is followed: a merge of a merge of a merge… never a loop. */
    static final int MAX_HOPS = 20;

    /** The most recent stays the summary shows. */
    static final int LAST_STAYS = 3;

    /** Where a consumer deduplicates what it receives (at-least-once delivery). */
    public interface Inbox {
        boolean firstTime(String consumer, String messageId);
    }

    // ── what it answers ─────────────────────────────────────────────────────────

    /** A customer's history at a glance. Zeros (and nulls for the dates) for a customer with no stays. */
    public record Summary(String customerId, int stays, int nights, LocalDate firstStay, LocalDate lastStay,
                          List<LastStay> lastStays, int hotels, String topHotel, Spend spend) {
    }

    public record LastStay(String hotelCode, LocalDate arrival, LocalDate departure, String roomNumber, String roomType) {
    }

    /** What the customer spent at the desks (accommodation not included), in its most frequent currency. */
    public record Spend(BigDecimal amount, String currency) {
    }

    /** A page of a customer's stays, newest departure first. */
    public record StayPage(String customerId, int total, List<StayItem> items) {
    }

    /** One stay, whole: with what it spent per kind of charge. {@code customerId} is the code it was closed with. */
    public record StayItem(String customerId, String hotelCode, String stayId, String crsLocator, LocalDate arrival,
                           LocalDate departure, int nights, String roomNumber, String roomType, String board,
                           boolean holder, BigDecimal addOnTotal, BigDecimal lateCheckOutTotal,
                           BigDecimal consumptionTotal, BigDecimal total, String currency, Instant closedAt,
                           String source) {

        static StayItem of(CustomerStay s) {
            return new StayItem(s.customerId, s.hotelCode, s.stayId, s.crsLocator, s.arrival, s.departure, s.nights,
                    s.roomNumber, s.roomType, s.board, s.holder, s.addOnTotal, s.lateCheckOutTotal, s.consumptionTotal,
                    s.total, s.currency, s.closedAt, s.source);
        }
    }

    final CustomerStayRepository stays;
    final CustomerAliasRepository aliases;
    final Inbox inbox;
    final Clock clock;

    public CustomerHistory(CustomerStayRepository stays, CustomerAliasRepository aliases, Inbox inbox, Clock clock) {
        this.stays = stays;
        this.aliases = aliases;
        this.inbox = inbox;
        this.clock = clock;
    }

    // ── what it takes ───────────────────────────────────────────────────────────

    /**
     * A stay the desk closed: one row for each of its guests the MDM knows ({@code C-…}). The others —
     * the front office's own ids ({@code pax-2}, {@code opera-…}, {@code wi-…}) — cannot be tied to a
     * customer, and are left out. Once per event (inbox), and idempotent besides: a row per (stay,
     * customer), written over if the stay is closed again.
     *
     * @return how many customers' rows it wrote; 0 for an event already taken
     */
    @Transactional
    public int take(StayClosed event) {
        if (event.eventId() != null && !inbox.firstTime(FRONT_OFFICE_EVENTS, event.eventId())) {
            return 0;
        }
        var byKind = new HashMap<ChargeKind, BigDecimal>();
        if (event.charges() != null) {
            event.charges().stream().filter(c -> c != null && c.kind() != null && c.amount() != null)
                    .forEach(c -> byKind.merge(c.kind(), c.amount(), BigDecimal::add));
        }
        var total = event.total() != null ? event.total()
                : byKind.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        var written = 0;
        var seen = new HashSet<String>();
        for (var guest : event.guests() == null ? List.<FrontOfficeEvent.StayGuest>of() : event.guests()) {
            if (guest == null || !isCustomerCode(guest.customerId()) || !seen.add(guest.customerId())) {
                continue;
            }
            var row = new CustomerStay();
            row.id = CustomerStay.id(event.stayId(), guest.customerId());
            row.customerId = guest.customerId();
            row.hotelCode = event.hotelCode();
            row.stayId = event.stayId();
            row.crsLocator = event.crsLocator();
            row.arrival = event.arrival();
            row.departure = event.departure();
            row.nights = event.nights();
            row.roomNumber = event.roomNumber();
            row.roomType = event.roomType();
            row.board = event.board();
            row.holder = guest.holder();
            row.addOnTotal = byKind.getOrDefault(ChargeKind.ADD_ON, BigDecimal.ZERO);
            row.lateCheckOutTotal = byKind.getOrDefault(ChargeKind.LATE_CHECK_OUT, BigDecimal.ZERO);
            row.consumptionTotal = byKind.getOrDefault(ChargeKind.CONSUMPTION, BigDecimal.ZERO);
            row.total = total;
            row.currency = event.currency();
            row.closedAt = event.at();
            row.source = CustomerStay.FRONT_OFFICE;
            stays.save(row);
            written++;
        }
        log.info("Stay {}/{} closed: {} customer(s) of the chain on it", event.hotelCode(), event.stayId(), written);
        return written;
    }

    /**
     * The MDM merged two customers: the absorbed code is from now on the survivor, for every stay it
     * has and will have. Once per event (inbox); the latest merge of a code wins.
     */
    @Transactional
    public void take(CustomersMerged event) {
        if (event.eventId() != null && !inbox.firstTime(CUSTOMERS, event.eventId())) {
            return;
        }
        var absorbed = normalized(event.absorbedId());
        var survivor = normalized(event.customerId());
        if (absorbed.isEmpty() || survivor.isEmpty() || absorbed.equals(survivor)) {
            log.warn("Merge {} names no two customers ({} into {}): ignored", event.eventId(), absorbed, survivor);
            return;
        }
        // A merge undoing an earlier one (the survivor was absorbed into this code before) would close a
        // loop: the survivor stops being an alias.
        if (survivorOf(survivor).equals(absorbed)) {
            aliases.deleteById(survivor);
        }
        var alias = aliases.findById(absorbed).orElseGet(CustomerAlias::new);
        alias.absorbedId = absorbed;
        alias.survivorId = survivor;
        alias.mergedAt = event.occurredAt() != null ? event.occurredAt() : clock.instant();
        aliases.save(alias);
        log.info("Customer {} merged into {}", absorbed, survivor);
    }

    // ── what it answers ─────────────────────────────────────────────────────────

    /** The customer's history at a glance: the survivor's, with every code merged into it. */
    @Transactional(readOnly = true)
    public Summary summary(String code) {
        var survivor = survivorOf(normalized(code));
        var rows = rowsOf(survivor);
        if (rows.isEmpty()) {
            return new Summary(survivor, 0, 0, null, null, List.of(), 0, null, new Spend(BigDecimal.ZERO.setScale(2), null));
        }
        var nights = rows.stream().mapToInt(s -> s.nights).sum();
        var first = rows.stream().map(s -> s.arrival).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        var last = rows.stream().map(s -> s.departure).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        var lastStays = rows.stream().limit(LAST_STAYS)
                .map(s -> new LastStay(s.hotelCode, s.arrival, s.departure, s.roomNumber, s.roomType)).toList();
        // stays per hotel, in the order of the latest stay at each: a tie goes to the most recent
        var perHotel = new LinkedHashMap<String, Integer>();
        rows.stream().filter(s -> s.hotelCode != null).forEach(s -> perHotel.merge(s.hotelCode, 1, Integer::sum));
        return new Summary(survivor, rows.size(), nights, first, last, lastStays, perHotel.size(),
                mostFrequent(perHotel), spend(rows));
    }

    /** A page of the customer's stays, newest departure first, merged codes included. */
    @Transactional(readOnly = true)
    public StayPage stays(String code, int page, int size) {
        var survivor = survivorOf(normalized(code));
        var rows = rowsOf(survivor);
        var pageSize = Math.max(1, Math.min(size, 100));
        var from = (long) Math.max(0, page) * pageSize;
        var items = rows.stream().skip(from).limit(pageSize).map(StayItem::of).toList();
        return new StayPage(survivor, rows.size(), items);
    }

    /** Where a code ends after every merge: itself if it was never absorbed. */
    @Transactional(readOnly = true)
    public String survivorOf(String code) {
        var current = code;
        var visited = new HashSet<String>();
        for (int hop = 0; hop < MAX_HOPS && visited.add(current); hop++) {
            var alias = aliases.findById(current);
            if (alias.isEmpty()) {
                break;
            }
            current = alias.get().survivorId;
        }
        return current;
    }

    /** The survivor and every code whose alias chain ends in it — one indexed query per level. */
    Set<String> codesOf(String survivor) {
        var codes = new LinkedHashSet<String>();
        codes.add(survivor);
        Set<String> frontier = Set.of(survivor);
        for (int level = 0; level < MAX_HOPS && !frontier.isEmpty(); level++) {
            var next = new HashSet<String>();
            for (var alias : aliases.findBySurvivorIdIn(frontier)) {
                if (codes.add(alias.absorbedId)) {
                    next.add(alias.absorbedId);
                }
            }
            frontier = next;
        }
        return codes;
    }

    /**
     * The customer's stays, newest departure first: the rows of every code of theirs, in one query on the
     * (customerId, departure) index, and one per stay — two of their codes on the same stay are still one
     * stay. A customer has tens of stays, not thousands: the summary is computed on them here.
     */
    List<CustomerStay> rowsOf(String survivor) {
        if (survivor.isEmpty()) {
            return List.of();
        }
        var unique = new LinkedHashMap<String, CustomerStay>();
        for (var s : stays.findByCustomerIdInOrderByDepartureDescStayIdAsc(codesOf(survivor))) {
            unique.putIfAbsent(s.stayId, s);
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * What they spent, in the currency most of their stays were closed in. Mixed currencies are not
     * converted (it is a PoC): only the stays in that currency are added up — adding MUR to EUR would be
     * a number that means nothing.
     */
    static Spend spend(List<CustomerStay> rows) {
        var perCurrency = new LinkedHashMap<String, Integer>();
        rows.forEach(s -> perCurrency.merge(currency(s), 1, Integer::sum));
        var chosen = mostFrequent(perCurrency);
        var amount = rows.stream().filter(s -> Objects.equals(currency(s), chosen))
                .map(s -> s.total == null ? BigDecimal.ZERO : s.total)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new Spend(amount, chosen.isEmpty() ? null : chosen);
    }

    /** The key counted most; on a tie the first one met — the rows come newest first, so the most recent. */
    static String mostFrequent(Map<String, Integer> counts) {
        String chosen = null;
        var best = -1;
        for (var e : counts.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                chosen = e.getKey();
            }
        }
        return chosen;
    }

    static String currency(CustomerStay s) {
        return s.currency == null ? "" : s.currency;
    }

    // ── the demo ────────────────────────────────────────────────────────────────

    /** The hotels the demo's past stays are at: codes in the CRS's style, of the chain's resorts. */
    static final List<String> DEMO_HOTELS = List.of("PMI01", "CUN01", "PUJ02", "MRU01", "TCI01", "CPV03");
    static final List<String> DEMO_ROOM_TYPES = List.of("DBL-SEA", "JS-SEA", "JS-STD", "DBL-STD");
    static final List<String> DEMO_BOARDS = List.of("TODO-INCLUIDO", "DESAYUNO", "MEDIA-PENSION");
    /**
     * One currency for every demo stay — the demo hotel's (MRU01, Mauritius): the spend is shown in the
     * most frequent currency and mixed ones are not converted, so the stays the demo closes at the desk
     * add up with these.
     */
    static final String DEMO_CURRENCY = "MUR";

    /**
     * Past stays for the demo, the same every time for the same customer: drawn from a seed of the code,
     * with ids {@code HIST-<code>-<n>}, so running it again writes the same rows (and a smaller count
     * leaves only those). Between one and four years ago, three to ten nights each.
     */
    @Transactional
    public Summary seedDemo(String customerId, Integer count) {
        var code = normalized(customerId);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Falta el código del cliente");
        }
        var n = count == null ? 4 : Math.max(1, Math.min(count, 8));
        stays.deleteByCustomerIdAndSource(code, CustomerStay.DEMO);
        var random = new Random(code.hashCode());
        var today = LocalDate.now(clock);
        var slot = (3 * 365) / n;
        for (int i = 1; i <= n; i++) {
            var nights = 3 + random.nextInt(8);
            var daysAgo = 365 + (n - i) * slot + random.nextInt(Math.max(1, slot - nights));
            var arrival = today.minusDays(daysAgo + nights);
            var row = new CustomerStay();
            row.stayId = "HIST-" + code + "-" + i;
            row.customerId = code;
            row.id = CustomerStay.id(row.stayId, code);
            row.hotelCode = DEMO_HOTELS.get(random.nextInt(DEMO_HOTELS.size()));
            row.crsLocator = locator(random);
            row.arrival = arrival;
            row.departure = arrival.plusDays(nights);
            row.nights = nights;
            row.roomNumber = String.valueOf((1 + random.nextInt(6)) * 100 + 1 + random.nextInt(30));
            row.roomType = DEMO_ROOM_TYPES.get(random.nextInt(DEMO_ROOM_TYPES.size()));
            row.board = DEMO_BOARDS.get(random.nextInt(DEMO_BOARDS.size()));
            row.holder = true;
            row.addOnTotal = random.nextInt(2) == 0 ? amount(random, 20, 150) : BigDecimal.ZERO.setScale(2);
            row.lateCheckOutTotal = random.nextInt(4) == 0 ? new BigDecimal("50.00") : BigDecimal.ZERO.setScale(2);
            row.consumptionTotal = random.nextInt(10) < 7 ? amount(random, 10, 300) : BigDecimal.ZERO.setScale(2);
            row.total = row.addOnTotal.add(row.lateCheckOutTotal).add(row.consumptionTotal);
            row.currency = DEMO_CURRENCY;
            row.closedAt = row.departure.atTime(LocalTime.of(11, 0)).toInstant(ZoneOffset.UTC);
            row.source = CustomerStay.DEMO;
            stays.save(row);
        }
        log.info("Demo history for {}: {} past stay(s)", code, n);
        return summary(code);
    }

    /** Takes the demo's stays of that customer away; the ones the front office closed stay. */
    @Transactional
    public int deleteDemo(String customerId) {
        return stays.deleteByCustomerIdAndSource(normalized(customerId), CustomerStay.DEMO);
    }

    static BigDecimal amount(Random random, int min, int max) {
        return BigDecimal.valueOf(min * 100L + random.nextInt((max - min) * 100), 2);
    }

    static String locator(Random random) {
        var chars = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        var sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /** A customer of the MDM: its codes are {@code C-…}; the front office's own ids are not. */
    public static boolean isCustomerCode(String id) {
        return id != null && id.startsWith("C-");
    }

    static String normalized(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
