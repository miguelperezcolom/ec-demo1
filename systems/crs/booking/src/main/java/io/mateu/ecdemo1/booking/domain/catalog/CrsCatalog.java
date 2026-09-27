package io.mateu.ecdemo1.booking.domain.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The CRS's own codes: hotels, room types, rate plans, boards, channels, cancellation reasons and
 * payment methods.
 *
 * <p>They are deliberately the CRS's and not the PMS's. That the two sides code the same things
 * differently is what the integration's mapping exists for, so a simulated CRS that spoke Opera's
 * codes would leave the part of the connector worth measuring with nothing to do.
 */
public record CrsCatalog(List<Hotel> hotels,
                         List<RatePlan> ratePlans,
                         List<Board> boards,
                         List<Channel> channels,
                         List<Code> cancellationReasons,
                         List<Code> paymentMethods) {

    /**
     * {@code codes} are the hotel's own rate plans, boards, channels, cancellation reasons and payment
     * methods, in place of the chain's; null, the hotel sells with the chain's.
     */
    public record Hotel(String code, String name, String currency, List<RoomType> roomTypes, Codes codes) {

        public Hotel(String code, String name, String currency, List<RoomType> roomTypes) {
            this(code, name, currency, roomTypes, null);
        }

        public Optional<RoomType> roomType(String code) {
            return roomTypes.stream().filter(r -> r.code().equals(code)).findFirst();
        }
    }

    /** The codes a hotel sells with, other than its room types. */
    public record Codes(List<RatePlan> ratePlans,
                        List<Board> boards,
                        List<Channel> channels,
                        List<Code> cancellationReasons,
                        List<Code> paymentMethods) {
    }

    /** {@code basePrice} is one night of the room alone, before rate plan, board and weekday. */
    public record RoomType(String code, String name, int maxOccupancy, BigDecimal basePrice) {
    }

    /** {@code factor} multiplies the room's base price: 0.90 is ten percent off. */
    public record RatePlan(String code, String name, BigDecimal factor) {
    }

    /** {@code supplementPerAdult} is per adult and night; children pay half, infants nothing. */
    public record Board(String code, String name, BigDecimal supplementPerAdult) {
    }

    /** A channel whose sales always come through a trading partner requires one on the booking. */
    public record Channel(String code, String name, boolean requiresPartner) {
    }

    public record Code(String code, String name) {
    }

    public Hotel hotel(String code) {
        return find(hotels, Hotel::code, code, "hotel");
    }

    /** The codes this hotel sells with: its own, or the chain's. */
    public Codes codes(String hotelCode) {
        var own = hotel(hotelCode).codes();
        return own != null ? own : new Codes(ratePlans, boards, channels, cancellationReasons, paymentMethods);
    }

    public RoomType roomType(String hotelCode, String code) {
        var hotel = hotel(hotelCode);
        return hotel.roomType(code).orElseThrow(() -> unknown(
                "room type of hotel " + hotelCode, code, hotel.roomTypes().stream().map(RoomType::code).toList()));
    }

    public RatePlan ratePlan(String hotelCode, String code) {
        return find(codes(hotelCode).ratePlans(), RatePlan::code, code, "rate plan of hotel " + hotelCode);
    }

    public Board board(String hotelCode, String code) {
        return find(codes(hotelCode).boards(), Board::code, code, "board of hotel " + hotelCode);
    }

    public Channel channel(String hotelCode, String code) {
        return find(codes(hotelCode).channels(), Channel::code, code, "channel of hotel " + hotelCode);
    }

    public Code cancellationReason(String hotelCode, String code) {
        return find(codes(hotelCode).cancellationReasons(), Code::code, code, "cancellation reason of hotel " + hotelCode);
    }

    public Code paymentMethod(String hotelCode, String code) {
        return find(codes(hotelCode).paymentMethods(), Code::code, code, "payment method of hotel " + hotelCode);
    }

    /** Every hotel's codes and the chain's, each code once — for a choice made before the hotel is known. */
    public <T> List<T> acrossHotels(java.util.function.Function<Codes, List<T>> list,
                                   java.util.function.Function<T, String> codeOf) {
        var all = new java.util.LinkedHashMap<String, T>();
        list.apply(new Codes(ratePlans, boards, channels, cancellationReasons, paymentMethods))
                .forEach(item -> all.putIfAbsent(codeOf.apply(item), item));
        hotels.stream().filter(h -> h.codes() != null)
                .forEach(h -> list.apply(h.codes()).forEach(item -> all.putIfAbsent(codeOf.apply(item), item)));
        return List.copyOf(all.values());
    }

    private static <T> T find(List<T> items, java.util.function.Function<T, String> codeOf, String code,
                              String what) {
        return items.stream().filter(item -> codeOf.apply(item).equals(code)).findFirst()
                .orElseThrow(() -> unknown(what, code, items.stream().map(codeOf).toList()));
    }

    private static IllegalArgumentException unknown(String what, String code, List<String> valid) {
        return new IllegalArgumentException("Unknown %s '%s'. Valid: %s"
                .formatted(what, code, valid.stream().collect(Collectors.joining(", "))));
    }

    /** The file a hotel's catalog imported from its PMS's is versioned in, next to the classes. */
    static final String IMPORTED = "/crs-catalog/%s.json";

    /**
     * A hotel whose catalog was imported from its Opera property's — every code the CRS's own, named
     * its own way, but each with a pair on the other side (deploy/demo/crs-catalog/generate.py makes
     * the file, and says which pair). Read from the classpath: the CRS never calls Opera.
     */
    static Hotel imported(String hotelCode) {
        try (var in = CrsCatalog.class.getResourceAsStream(IMPORTED.formatted(hotelCode))) {
            if (in == null) {
                throw new IllegalStateException("No imported catalog for " + hotelCode);
            }
            var file = new com.fasterxml.jackson.databind.ObjectMapper().readValue(in, ImportedHotel.class);
            return new Hotel(file.hotel().code(), file.hotel().name(), file.hotel().currency(), file.roomTypes(),
                    new Codes(file.ratePlans(), file.boards(), file.channels(), file.cancellationReasons(),
                            file.paymentMethods()));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Unreadable imported catalog for " + hotelCode, e);
        }
    }

    record ImportedHotel(HotelHeader hotel, List<RoomType> roomTypes, List<RatePlan> ratePlans, List<Board> boards,
                         List<Channel> channels, List<Code> cancellationReasons, List<Code> paymentMethods) {
    }

    record HotelHeader(String code, String name, String currency) {
    }

    public static CrsCatalog standard() {
        return new CrsCatalog(
                List.of(
                        new Hotel("PMI01", "Riu Demo Palma", "EUR", List.of(
                                new RoomType("IND", "Individual", 1, new BigDecimal("90")),
                                new RoomType("DBL", "Doble estándar", 3, new BigDecimal("120")),
                                new RoomType("DBLSV", "Doble vista mar", 3, new BigDecimal("150")),
                                new RoomType("JSU", "Junior suite", 4, new BigDecimal("210")))),
                        new Hotel("CUN01", "Riu Demo Cancún", "USD", List.of(
                                new RoomType("DBL", "Doble estándar", 3, new BigDecimal("160")),
                                new RoomType("DBLOV", "Doble vista océano", 3, new BigDecimal("195")),
                                new RoomType("JSU", "Junior suite", 4, new BigDecimal("260")),
                                new RoomType("SUI", "Suite", 4, new BigDecimal("380")))),
                        // Integrated with a real Opera tenant's pilot property (XMAR, "Piloto Mauricio"),
                        // and its catalog imported from that property's: see imported(String).
                        imported("MRU01")),
                List.of(
                        new RatePlan("BAR", "Tarifa pública", new BigDecimal("1.00")),
                        new RatePlan("NRF", "No reembolsable", new BigDecimal("0.90")),
                        new RatePlan("EB", "Early booking", new BigDecimal("0.85")),
                        new RatePlan("TTOO", "Contrato turoperador", new BigDecimal("0.80"))),
                List.of(
                        new Board("SA", "Solo alojamiento", new BigDecimal("0")),
                        new Board("AD", "Alojamiento y desayuno", new BigDecimal("15")),
                        new Board("MP", "Media pensión", new BigDecimal("35")),
                        new Board("PC", "Pensión completa", new BigDecimal("50")),
                        new Board("TI", "Todo incluido", new BigDecimal("80"))),
                List.of(
                        new Channel("WEB", "Web propia", false),
                        new Channel("CC", "Call center", false),
                        new Channel("RECEP", "Recepción (walk-in)", false),
                        new Channel("TTOO", "Turoperador", true),
                        new Channel("OTA", "Agencia online", true)),
                List.of(
                        new Code("CLI", "A petición del cliente"),
                        new Code("IMP", "Impago"),
                        new Code("DUP", "Reserva duplicada"),
                        new Code("OVB", "Sobreventa / reubicación"),
                        new Code("OTR", "Otros"),
                        new Code("NOS", "No show")),
                List.of(
                        new Code("VISA", "Visa"),
                        new Code("MC", "Mastercard"),
                        new Code("AMEX", "American Express"),
                        new Code("TRF", "Transferencia"),
                        new Code("EFE", "Efectivo")));
    }
}
