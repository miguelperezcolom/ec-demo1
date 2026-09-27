package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.out.partners.PartnerDirectory.TradingPartner;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.GuestType;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Stay;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Plausible bookings for a demo, built the way the booking form and the wizard build theirs — rooms,
 * guests and payments as the form holds them, turned into a request by {@link BookingRequests} — so
 * that creating them goes through the same validations, prices and events as a booking made by hand.
 *
 * <p>Only the catalog's codes are used: the hotel's room types, rate plans, boards and channels — MRU01's
 * own, imported from its Opera property's (deploy/demo/crs-catalog). A channel that sells through a partner gets one of the partners given,
 * of a type that makes sense for it; with none to pick from, the booking is a direct one instead.
 *
 * <p>The same {@link Random} seed and the same day give the same bookings. The external references
 * carry {@code batch}, which the caller makes unique per run, so a seed used twice does not repeat
 * them.
 */
final class DemoBookingGenerator {

    static final String HOTEL = "MRU01";
    static final int MIN_DAYS_AHEAD = 14;
    static final int MAX_DAYS_AHEAD = 56;
    static final int MIN_NIGHTS = 2;
    static final int MAX_NIGHTS = 7;

    /** One booking to create: the request, and the payments to register once it exists. */
    record DemoBooking(String hotelCode, BookingRequest request, List<PaymentViewModel> payments) {
    }

    /** The mix of a batch of ten; a batch of another size follows it in proportion. */
    static final List<String> CHANNEL_MIX = List.of("WEB", "WEB", "WEB", "CALLCENTER", "CALLCENTER", "TTOO", "TTOO", "TTOO", "OTA", "OTA");

    /** Its call center's bookings carry no reference of their own: a web or a partner booking does. */
    static final String CALL_CENTER = "CALLCENTER";

    /**
     * A rate plan sold with its board: in XMAR, EXP_BB (Expedia's) and AGRO carry the breakfast package,
     * so they are sold with breakfast and never as room only — the connector then writes no package of
     * its own. Every other plan carries none, and takes any board (deploy/demo/crs-catalog/generate.py).
     */
    static final java.util.Map<String, String> BOARD_OF_RATE = java.util.Map.of("EXPEDIA-AD", "DESAYUNO",
            "AGRO-MAYOR", "DESAYUNO");

    /** Accounts for flight delays and staff travel sell nothing: never a demo booking's partner. */
    static final Pattern NOT_A_SELLER = Pattern.compile("(?i)retraso|delay|staff|crew");
    /** The names that read as an online agency, preferred for the OTA channel when there are any. */
    static final Pattern ONLINE_AGENCY = Pattern.compile(
            "(?i)\\bbooking\\b|hotelbeds|bedsonline|agoda|expedia|w2m|traveltool|bookit|jumbonline");

    record Country(String code, String phonePrefix, List<String> firstNames, List<String> lastNames) {
    }

    static final List<Country> COUNTRIES = List.of(
            new Country("ES", "+34 6", List.of("Lucía", "Javier", "Carmen", "Pablo", "Marta", "Álvaro"),
                    List.of("García", "Fernández", "López", "Martín", "Sánchez", "Romero")),
            new Country("FR", "+33 6", List.of("Camille", "Julien", "Chloé", "Antoine", "Léa", "Mathieu"),
                    List.of("Martin", "Dubois", "Lefèvre", "Moreau", "Laurent", "Girard")),
            new Country("DE", "+49 15", List.of("Anna", "Lukas", "Sophie", "Maximilian", "Lena", "Jonas"),
                    List.of("Müller", "Schmidt", "Schneider", "Fischer", "Weber", "Becker")),
            new Country("IT", "+39 3", List.of("Giulia", "Marco", "Chiara", "Alessandro", "Francesca", "Luca"),
                    List.of("Rossi", "Bianchi", "Romano", "Colombo", "Ricci", "Greco")),
            new Country("GB", "+44 7", List.of("Olivia", "James", "Emily", "Oliver", "Charlotte", "Harry"),
                    List.of("Smith", "Jones", "Taylor", "Brown", "Wilson", "Evans")),
            new Country("NL", "+31 6", List.of("Emma", "Daan", "Sanne", "Bram", "Fleur", "Thijs"),
                    List.of("de Vries", "Jansen", "van Dijk", "Bakker", "Visser", "Smit")),
            new Country("PT", "+351 9", List.of("Beatriz", "João", "Inês", "Tiago", "Mariana", "Rui"),
                    List.of("Silva", "Santos", "Ferreira", "Pereira", "Costa", "Oliveira")),
            new Country("SE", "+46 7", List.of("Elsa", "Erik", "Maja", "Oskar", "Ebba", "Gustav"),
                    List.of("Andersson", "Johansson", "Karlsson", "Nilsson", "Lindberg", "Berg")),
            new Country("PL", "+48 5", List.of("Zofia", "Jakub", "Julia", "Piotr", "Natalia", "Tomasz"),
                    List.of("Nowak", "Kowalski", "Wiśniewski", "Wójcik", "Kamiński", "Lewandowski")),
            new Country("IE", "+353 8", List.of("Aoife", "Conor", "Siobhán", "Cian", "Niamh", "Seán"),
                    List.of("Murphy", "Kelly", "O'Brien", "Byrne", "Ryan", "Walsh")),
            new Country("BE", "+32 4", List.of("Louise", "Arthur", "Elise", "Victor", "Marie", "Lucas"),
                    List.of("Peeters", "Janssens", "Maes", "Claes", "Wouters", "Dupont")),
            new Country("AT", "+43 6", List.of("Hannah", "Tobias", "Laura", "Florian", "Katharina", "Stefan"),
                    List.of("Gruber", "Huber", "Wagner", "Pichler", "Steiner", "Moser")));

    static final List<String> COMMENTS = List.of(
            "Honeymoon", "Late arrival, around 23:00", "Quiet room if possible", "Celebrating an anniversary",
            "Cot needed in the room", "Gluten-free meals");

    final CrsCatalog catalog;
    final RoomPricing pricing;
    final List<TradingPartner> partners;
    final Random random;
    final LocalDate today;
    final String batch;

    DemoBookingGenerator(CrsCatalog catalog, RoomPricing pricing, List<TradingPartner> partners, Random random,
                         LocalDate today, String batch) {
        this.catalog = catalog;
        this.pricing = pricing;
        this.partners = partners != null ? partners : List.of();
        this.random = random;
        this.today = today;
        this.batch = batch;
    }

    List<DemoBooking> generate(int count) {
        var channels = new ArrayList<String>();
        var families = new ArrayList<Boolean>();
        for (int i = 0; i < count; i++) {
            channels.add(CHANNEL_MIX.get(i % CHANNEL_MIX.size()));
            families.add(i % 3 == 0);
        }
        Collections.shuffle(channels, random);
        Collections.shuffle(families, random);
        var bookings = new ArrayList<DemoBooking>();
        var paidOne = false;
        for (int i = 0; i < count; i++) {
            var booking = booking(i, channels.get(i), families.get(i), !paidOne);
            paidOne |= !booking.payments().isEmpty();
            bookings.add(booking);
        }
        return bookings;
    }

    /** {@code mustPay}: no booking of the batch is paid yet, so a direct one is. */
    DemoBooking booking(int index, String wantedChannel, boolean family, boolean mustPay) {
        var hotel = catalog.hotel(HOTEL);
        var codes = catalog.codes(HOTEL);
        var channel = catalog.channel(HOTEL, wantedChannel);
        TradingPartner partner = null;
        if (channel.requiresPartner()) {
            partner = partnerFor(channel.code());
            if (partner == null) {
                channel = catalog.channel(HOTEL, "WEB");
            }
        }
        var arrival = today.plusDays(between(MIN_DAYS_AHEAD, MAX_DAYS_AHEAD));
        var departure = arrival.plusDays(between(MIN_NIGHTS, MAX_NIGHTS));
        var country = pick(COUNTRIES);
        var firstName = pick(country.firstNames());
        var lastName = pick(country.lastNames());
        var holder = new Holder(firstName, lastName, email(firstName, lastName), phone(country), country.code());

        var ratePlan = ratePlan(channel.code());
        var board = BOARD_OF_RATE.containsKey(ratePlan) ? BOARD_OF_RATE.get(ratePlan)
                : pick(existing(codes.boards().stream().map(CrsCatalog.Board::code).toList(),
                List.of("SOLO-ALOJAMIENTO", "DESAYUNO", "DESAYUNO", "COMIDAS", "COMIDAS", "TODO-INCLUIDO",
                        "TODO-INCLUIDO", "TODO-INCLUIDO")));
        var roomCount = random.nextInt(10) < 7 ? 1 : 2;
        var rooms = new ArrayList<RoomViewModel>();
        var guests = new ArrayList<GuestViewModel>();
        for (int line = 1; line <= roomCount; line++) {
            var roomType = pick(hotel.roomTypes());
            int adults;
            var childrenAges = new ArrayList<Integer>();
            if (family && line == 1 && roomType.maxOccupancy() >= 2) {
                adults = roomType.maxOccupancy() >= 3 && random.nextBoolean() ? 2 : 1;
                var children = Math.max(1, Math.min(roomType.maxOccupancy() - adults, 1 + random.nextInt(2)));
                for (int c = 0; c < children; c++) {
                    childrenAges.add(between(2, 12));
                }
            } else {
                adults = Math.min(roomType.maxOccupancy(), weightedAdults());
            }
            rooms.add(new RoomViewModel(null, roomType.code(), ratePlan, board, adults, childrenAges, null));
            var roomLastName = line == 1 ? lastName : pick(country.lastNames());
            for (int a = 0; a < adults; a++) {
                var first = line == 1 && a == 0 ? holder.firstName() : pick(country.firstNames());
                guests.add(new GuestViewModel(line, first, roomLastName, GuestType.Adult, null, null,
                        country.code(), null, null));
            }
            for (var age : childrenAges) {
                guests.add(new GuestViewModel(line, pick(country.firstNames()), roomLastName, GuestType.Child, age,
                        null, country.code(), null, null));
            }
        }

        var direct = !channel.requiresPartner();
        var payments = new ArrayList<PaymentViewModel>();
        if (direct && (mustPay || random.nextBoolean())) {
            payments.add(payment(hotel, rooms, arrival, departure));
        }
        // The partner's voucher, or the web's own locator; a call center booking has none.
        var reference = CALL_CENTER.equals(channel.code()) ? null : "%s-%s-%02d".formatted(channel.code(), batch, index + 1);
        var comments = random.nextInt(10) < 3 ? pick(COMMENTS) : null;
        var request = BookingRequests.of(channel.code(), partner != null ? partner.code() : null, reference,
                arrival, departure, holder, rooms, guests, comments);
        return new DemoBooking(hotel.code(), request, payments);
    }

    /** A partner that sells through this channel: tour operators for TTOO, online agencies for OTA. */
    TradingPartner partnerFor(String channelCode) {
        var types = switch (channelCode) {
            case "TTOO" -> List.of("TourOperator", "TravelAgent");
            case "OTA" -> List.of("OnlineAgency", "Company");
            default -> List.of("TourOperator", "TravelAgent", "OnlineAgency", "Company");
        };
        var candidates = partners.stream()
                .filter(p -> types.contains(p.type()))
                .filter(p -> p.name() == null || !NOT_A_SELLER.matcher(p.name()).find())
                .toList();
        if ("OTA".equals(channelCode)) {
            var online = candidates.stream().filter(p -> p.name() != null && ONLINE_AGENCY.matcher(p.name()).find())
                    .toList();
            if (!online.isEmpty()) {
                candidates = online;
            }
        }
        return candidates.isEmpty() ? null : pick(candidates);
    }

    /**
     * A tour operator's or a local agency's contract for TTOO, an online agency's plan for OTA, and the
     * direct sale's otherwise — the flexible one for residents only now and then. XMAR has no early
     * booking, so neither has MRU01.
     */
    String ratePlan(String channelCode) {
        var valid = catalog.codes(HOTEL).ratePlans().stream().map(CrsCatalog.RatePlan::code).toList();
        var wanted = switch (channelCode) {
            case "TTOO" -> List.of("TUI-NL", "TUI-FR", "DMC-MAURICIO", "AGENCIAS-LOCALES");
            case "OTA" -> List.of("EXPEDIA-AD", "AGRO-MAYOR");
            default -> List.of("DIRECTA", "DIRECTA", "DIRECTA", "FLEX-LOCAL");
        };
        var options = existing(valid, wanted);
        return options.isEmpty() ? valid.get(0) : pick(options);
    }

    /** A deposit of about a third, or the whole stay, worked out with the CRS's own pricing. */
    PaymentViewModel payment(CrsCatalog.Hotel hotel, List<RoomViewModel> rooms, LocalDate arrival, LocalDate departure) {
        var stay = new Stay(arrival, departure);
        var total = rooms.stream()
                .flatMap(r -> pricing.price(catalog.roomType(hotel.code(), r.roomTypeCode()),
                        catalog.ratePlan(hotel.code(), r.ratePlanCode()), catalog.board(hotel.code(), r.boardCode()), r.adults(),
                        r.childrenAges(), stay).stream())
                .map(n -> n.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var prepaid = random.nextInt(3) == 0;
        var amount = prepaid ? total : total.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.HALF_UP);
        var methods = existing(catalog.codes(hotel.code()).paymentMethods().stream().map(CrsCatalog.Code::code).toList(),
                List.of("VISA", "VISA", "MASTERCARD", "VISA-MANUAL", "TRANSFERENCIA"));
        return new PaymentViewModel(null, prepaid ? PaymentType.Prepayment : PaymentType.Deposit, pick(methods),
                amount, today, "AUTH-%06d".formatted(random.nextInt(1_000_000)));
    }

    int weightedAdults() {
        var r = random.nextInt(10);
        return r < 2 ? 1 : r < 8 ? 2 : 3;
    }

    String email(String firstName, String lastName) {
        return "%s.%s@example.com".formatted(ascii(firstName), ascii(lastName));
    }

    String phone(Country country) {
        var digits = new StringBuilder();
        while (digits.length() + country.phonePrefix().replaceAll("\\D", "").length() < 11) {
            digits.append(random.nextInt(10));
        }
        return country.phonePrefix() + digits;
    }

    static String ascii(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("ł", "l").replace("Ł", "L")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]", "");
    }

    /** The wanted codes the catalog has, in the order (and with the repetitions) they are wanted. */
    static List<String> existing(List<String> valid, List<String> wanted) {
        return wanted.stream().filter(valid::contains).toList();
    }

    int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }
}
