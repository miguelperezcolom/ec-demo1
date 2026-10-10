package io.mateu.ecdemo1.booking.infra.in.mcp;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingCriteria;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.confirm.ConfirmBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.confirm.ConfirmBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.update.UpdateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.update.UpdateBookingUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookedRoom;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Guest;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.workflow.mcp.McpSystemContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingMcpTools implements McpSystemContext {

    @Override
    public String getSystemContext() {
        return """
                Servicio de reservas (el CRS):
                - Una reserva pertenece a un hotel y tiene canal, fechas de entrada y salida, titular
                  (holder), una o más habitaciones y, opcionalmente, un interlocutor (partnerCode) y su
                  referencia (externalReference, el bono).
                - Cada habitación lleva tipo de habitación, tarifa (ratePlan) y régimen (board), adultos,
                  edades de los niños y, si se conocen, sus huéspedes. El CRS calcula el precio de cada
                  noche: no se lo pases.
                - Todos los códigos son los del CRS. Consulta getCatalog antes de crear o modificar si no
                  los conoces; los canales TTOO y OTA exigen partnerCode.
                - Una modificación sustituye las condiciones completas: envía la reserva entera, no solo
                  lo que cambia.
                - Estados: Confirmed (una reserva nace confirmada), Cancelled; Pending sólo lo tienen
                  reservas antiguas, y se confirman con confirmBooking. Una reserva cancelada no se
                  puede modificar ni confirmar.
                - Cada cambio incrementa la versión de la reserva.
                - Para cualquier pregunta que filtre reservas (hotel, estado, fechas, canal, interlocutor,
                  tipo de habitación, régimen, tarifa, nacionalidad o texto) usa searchBookings: una sola
                  llamada, y cada resultado ya trae sus habitaciones y nacionalidades. No leas las reservas
                  una a una con getBooking para filtrarlas.
                """;
    }

    private final BookingQueryService bookingQueryService;
    private final CreateBookingUseCase createBookingUseCase;
    private final UpdateBookingUseCase updateBookingUseCase;
    private final ConfirmBookingUseCase confirmBookingUseCase;
    private final CancelBookingUseCase cancelBookingUseCase;
    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final CrsCatalog catalog;

    public record BookingSummary(String id, String hotelCode, String holder, LocalDate arrival,
                                 LocalDate departure, String status, long version, BigDecimal total,
                                 String currency, String pmsReservationId) {
    }

    /** A room of a booking as a search shows it: its codes, the room type's name and who is in it. */
    public record RoomLine(String roomType, String roomTypeName, String ratePlan, String board, int adults,
                           List<Integer> childrenAges, List<String> guestNationalities) {
    }

    /** A booking as a search finds it: enough to answer about it without reading it in full. */
    public record BookingFound(String id, String hotelCode, String status, String channel, String partnerCode,
                               String externalReference, LocalDate arrival, LocalDate departure, int nights,
                               String holder, String holderNationality, List<RoomLine> rooms, BigDecimal total,
                               String currency, String pmsReservationId) {
    }

    /** The most a search looks through before applying what the database cannot (room, board, nationality). */
    static final int SEARCH_SCAN = 2000;
    static final int SEARCH_LIMIT = 50;
    static final int SEARCH_MAX = 200;

    @Tool(description = "The CRS's codes: hotels with their room types, rate plans, boards, channels, "
            + "cancellation reasons and payment methods. A hotel with codes of its own (MRU01) sells only "
            + "with those; the others, with the chain's")
    public CrsCatalog getCatalog() {
        return catalog;
    }

    @Tool(description = "List bookings, most recent first. The search text matches the booking id, the "
            + "holder's name or the hotel code; leave it empty to list all")
    public List<BookingSummary> listBookings(@ToolParam(required = false) String search) {
        log.info("MCP listBookings search={}", search);
        return bookingQueryService.list(search, 0, 50).stream()
                .map(b -> new BookingSummary(b.id(), b.hotelCode(), b.holder().fullName(), b.arrival(),
                        b.departure(), b.status().name(), b.version(), b.totalAmount(), b.currency(),
                        b.pmsReference() != null ? b.pmsReference().reservationId() : null))
                .toList();
    }

    @Tool(description = "Search bookings in ONE call with whatever filters the question has; every filter is "
            + "optional and they all apply. Each result carries its channel, holder and holder's nationality, and "
            + "its rooms (room type code and name, rate plan, board, occupancy, guests' nationalities), so there "
            + "is no need to read bookings one by one to filter them. E.g. «dobles de españoles que entran hoy en "
            + "MRU01»: hotelCode=MRU01, statuses=[Confirmed], arrivalFrom=arrivalTo=today, roomType=doble, "
            + "nationality=ES. By arrival; at most 50 unless limit says otherwise (max 200)")
    public List<BookingFound> searchBookings(
            @ToolParam(description = "CRS hotel code, e.g. MRU01", required = false) String hotelCode,
            @ToolParam(description = "Statuses, any of Pending, Confirmed, Cancelled; empty for all",
                    required = false) List<String> statuses,
            @ToolParam(description = "Arrival on or after this date, ISO yyyy-MM-dd", required = false)
            LocalDate arrivalFrom,
            @ToolParam(description = "Arrival on or before this date, ISO", required = false) LocalDate arrivalTo,
            @ToolParam(description = "Departure on or after this date, ISO", required = false) LocalDate departureFrom,
            @ToolParam(description = "Departure on or before this date, ISO", required = false) LocalDate departureTo,
            @ToolParam(description = "Channel code, e.g. WEB, TTOO, OTA", required = false) String channel,
            @ToolParam(description = "Partner (tour operator, OTA) code", required = false) String partnerCode,
            @ToolParam(description = "Part of a room's type code or name, any case (doble, DBL, suite…): "
                    + "bookings with a room of it", required = false) String roomType,
            @ToolParam(description = "A room's board code or part of its name (TI, AD, todo incluido…)",
                    required = false) String board,
            @ToolParam(description = "A room's rate plan code or part of its name", required = false) String ratePlan,
            @ToolParam(description = "Nationality, ISO-2 (ES, DE, GB…): the holder's or any guest's, unless "
                    + "holderOnly", required = false) String nationality,
            @ToolParam(description = "true: the nationality must be the holder's", required = false) Boolean holderOnly,
            @ToolParam(description = "Part of the booking id, the holder's name or the hotel code", required = false)
            String text,
            @ToolParam(description = "How many at most; 50 if empty, 200 at most", required = false) Integer limit) {
        log.info("MCP searchBookings hotel={} statuses={} arrival={}..{} roomType={} nationality={}", hotelCode,
                statuses, arrivalFrom, arrivalTo, roomType, nationality);
        var wanted = statuses == null || statuses.isEmpty() ? Set.<BookingStatus>of()
                : EnumSet.copyOf(statuses.stream().map(BookingMcpTools::status).toList());
        var max = limit == null || limit < 1 ? SEARCH_LIMIT : Math.min(limit, SEARCH_MAX);
        var criteria = new BookingCriteria(blankToNull(hotelCode), wanted, arrivalFrom, arrivalTo, departureFrom,
                departureTo);
        var iso = blankToNull(nationality) == null ? null : nationality.trim().toUpperCase(Locale.ROOT);
        return bookingQueryService.findAll(blankToNull(text), criteria,
                        PageRequest.of(0, SEARCH_SCAN, Sort.by("arrival", "id"))).stream()
                .filter(b -> matchesCode(channel, b.channelCode()))
                .filter(b -> matchesCode(partnerCode, b.partnerCode()))
                .filter(b -> roomType == null || roomType.isBlank() || b.rooms().stream()
                        .anyMatch(r -> contains(roomType, r.roomTypeCode(), roomTypeName(b.hotelCode(), r))))
                .filter(b -> board == null || board.isBlank() || b.rooms().stream()
                        .anyMatch(r -> contains(board, r.boardCode(), name(() -> catalog.board(b.hotelCode(),
                                r.boardCode()).name()))))
                .filter(b -> ratePlan == null || ratePlan.isBlank() || b.rooms().stream()
                        .anyMatch(r -> contains(ratePlan, r.ratePlanCode(), name(() -> catalog.ratePlan(
                                b.hotelCode(), r.ratePlanCode()).name()))))
                .filter(b -> iso == null || nationalities(b, Boolean.TRUE.equals(holderOnly)).contains(iso))
                .limit(max)
                .map(this::found)
                .toList();
    }

    BookingFound found(BookingDto b) {
        var rooms = b.rooms().stream().map(r -> new RoomLine(r.roomTypeCode(), roomTypeName(b.hotelCode(), r),
                r.ratePlanCode(), r.boardCode(), r.adults(), r.childrenAges(),
                r.guests().stream().map(Guest::nationality).filter(Objects::nonNull).toList())).toList();
        return new BookingFound(b.id(), b.hotelCode(), b.status().name(), b.channelCode(), b.partnerCode(),
                b.externalReference(), b.arrival(), b.departure(), b.nights(), b.holder().fullName(),
                b.holder().nationality(), rooms, b.totalAmount(), b.currency(),
                b.pmsReference() != null ? b.pmsReference().reservationId() : null);
    }

    /** The holder's nationality and — unless only the holder's counts — every guest's, in ISO upper case. */
    static Set<String> nationalities(BookingDto b, boolean holderOnly) {
        var all = new java.util.HashSet<String>();
        if (b.holder().nationality() != null) {
            all.add(b.holder().nationality().trim().toUpperCase(Locale.ROOT));
        }
        if (!holderOnly) {
            b.rooms().stream().flatMap(r -> r.guests().stream()).map(Guest::nationality).filter(Objects::nonNull)
                    .forEach(n -> all.add(n.trim().toUpperCase(Locale.ROOT)));
        }
        return all;
    }

    String roomTypeName(String hotelCode, BookedRoom room) {
        return name(() -> catalog.roomType(hotelCode, room.roomTypeCode()).name());
    }

    /** A catalog name, or null when the catalog no longer knows the code. */
    static String name(Supplier<String> lookup) {
        try {
            return lookup.get();
        } catch (RuntimeException unknown) {
            return null;
        }
    }

    /** Whether the text is part of the code or of the name, ignoring case. */
    static boolean contains(String text, String code, String name) {
        var t = text.trim().toLowerCase(Locale.ROOT);
        return code != null && code.toLowerCase(Locale.ROOT).contains(t)
                || name != null && name.toLowerCase(Locale.ROOT).contains(t);
    }

    static boolean matchesCode(String wanted, String code) {
        return wanted == null || wanted.isBlank() || wanted.trim().equalsIgnoreCase(code);
    }

    static BookingStatus status(String text) {
        return java.util.Arrays.stream(BookingStatus.values()).filter(s -> s.name().equalsIgnoreCase(text.trim()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown booking status " + text
                        + "; one of Pending, Confirmed, Cancelled"));
    }

    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    @Tool(description = "Read a booking in full: stay, holder, rooms with guests and nightly rates, "
            + "payments, status, version and, if it already reached the PMS, its reservation id there")
    public BookingDto getBooking(String id) {
        log.info("MCP getBooking {}", id);
        // Thrown, not returned: a tool returning Object is silently dropped by Spring AI ("returns a
        // functional type"), and an exception reaches the agent as the tool's error text.
        return bookingQueryService.getById(id).orElseThrow(() -> new java.util.NoSuchElementException("Booking not found: " + id));
    }

    @Tool(description = "Create a booking in a hotel. Returns its id. It is confirmed as it is made")
    public String createBooking(@ToolParam(description = "CRS hotel code, e.g. PMI01") String hotelCode,
                                BookingRequest booking) {
        log.info("MCP createBooking hotel={}", hotelCode);
        return attempt(() -> "Booking created with id "
                + createBookingUseCase.handle(new CreateBookingCommand(hotelCode, booking)));
    }

    @Tool(description = "Modify a booking. Replaces its terms as a whole — send the complete booking — "
            + "and the CRS prices it again")
    public String modifyBooking(String id, BookingRequest booking) {
        log.info("MCP modifyBooking {}", id);
        return attempt(() -> {
            return updateBookingUseCase.handle(new UpdateBookingCommand(id, booking))
                    ? "Booking %s modified".formatted(id)
                    : "Booking %s unchanged: the terms sent are the ones it has".formatted(id);
        });
    }

    @Tool(description = "Confirm a booking left pending: only old ones can be, a new booking is born confirmed")
    public String confirmBooking(String id) {
        log.info("MCP confirmBooking {}", id);
        return attempt(() -> {
            confirmBookingUseCase.handle(new ConfirmBookingCommand(id));
            return "Booking %s confirmed".formatted(id);
        });
    }

    @Tool(description = "Cancel a booking with one of its hotel's cancellation reasons, e.g. CLI "
            + "(at the customer's request) — OTR (other reasons) in MRU01, which has its own")
    public String cancelBooking(String id, String reasonCode) {
        log.info("MCP cancelBooking {} reason={}", id, reasonCode);
        return attempt(() -> {
            cancelBookingUseCase.handle(new CancelBookingCommand(id, reasonCode));
            return "Booking %s cancelled".formatted(id);
        });
    }

    @Tool(description = "Register money the central office has collected for a booking: a Deposit or a "
            + "Prepayment, with one of its hotel's payment methods. Returns the payment id")
    public String registerPayment(String id, PaymentType type, String methodCode, BigDecimal amount,
                                  @ToolParam(required = false) String reference) {
        log.info("MCP registerPayment {} {} {}", id, type, amount);
        return attempt(() -> "Payment registered with id " + registerPaymentUseCase.handle(
                new RegisterPaymentCommand(id, type, methodCode, amount, null, reference)));
    }

    /**
     * A refusal goes back to the agent as text rather than as a failed call, so it can read why —
     * an unknown code comes with the valid ones — and try again.
     */
    private static String attempt(Supplier<String> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }
}
