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

    public record Hotel(String code, String name, String currency, List<RoomType> roomTypes) {
        public Optional<RoomType> roomType(String code) {
            return roomTypes.stream().filter(r -> r.code().equals(code)).findFirst();
        }
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

    public RoomType roomType(String hotelCode, String code) {
        var hotel = hotel(hotelCode);
        return hotel.roomType(code).orElseThrow(() -> unknown(
                "room type of hotel " + hotelCode, code, hotel.roomTypes().stream().map(RoomType::code).toList()));
    }

    public RatePlan ratePlan(String code) {
        return find(ratePlans, RatePlan::code, code, "rate plan");
    }

    public Board board(String code) {
        return find(boards, Board::code, code, "board");
    }

    public Channel channel(String code) {
        return find(channels, Channel::code, code, "channel");
    }

    public Code cancellationReason(String code) {
        return find(cancellationReasons, Code::code, code, "cancellation reason");
    }

    public Code paymentMethod(String code) {
        return find(paymentMethods, Code::code, code, "payment method");
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
                                new RoomType("SUI", "Suite", 4, new BigDecimal("380"))))),
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
                        new Code("OTR", "Otros")),
                List.of(
                        new Code("VISA", "Visa"),
                        new Code("MC", "Mastercard"),
                        new Code("AMEX", "American Express"),
                        new Code("TRF", "Transferencia"),
                        new Code("EFE", "Efectivo")));
    }
}
