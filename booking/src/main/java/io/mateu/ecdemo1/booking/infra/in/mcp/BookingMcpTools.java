package io.mateu.ecdemo1.booking.infra.in.mcp;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
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
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.workflow.mcp.McpSystemContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
                - Estados: Pending (pendiente de pago), Confirmed, Cancelled. Una reserva cancelada no se
                  puede modificar ni confirmar.
                - Cada cambio incrementa la versión de la reserva.
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

    @Tool(description = "The CRS's codes: hotels with their room types, rate plans, boards, channels, "
            + "cancellation reasons and payment methods")
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

    @Tool(description = "Read a booking in full: stay, holder, rooms with guests and nightly rates, "
            + "payments, status, version and, if it already reached the PMS, its reservation id there")
    public BookingDto getBooking(String id) {
        log.info("MCP getBooking {}", id);
        // Thrown, not returned: a tool returning Object is silently dropped by Spring AI ("returns a
        // functional type"), and an exception reaches the agent as the tool's error text.
        return bookingQueryService.getById(id).orElseThrow(() -> new java.util.NoSuchElementException("Booking not found: " + id));
    }

    @Tool(description = "Create a booking in a hotel. Returns its id. It starts Pending until payment is verified")
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
            updateBookingUseCase.handle(new UpdateBookingCommand(id, booking));
            return "Booking %s modified".formatted(id);
        });
    }

    @Tool(description = "Confirm a pending booking")
    public String confirmBooking(String id) {
        log.info("MCP confirmBooking {}", id);
        return attempt(() -> {
            confirmBookingUseCase.handle(new ConfirmBookingCommand(id));
            return "Booking %s confirmed".formatted(id);
        });
    }

    @Tool(description = "Cancel a booking with one of the catalog's cancellation reasons, e.g. CLI "
            + "(at the customer's request)")
    public String cancelBooking(String id, String reasonCode) {
        log.info("MCP cancelBooking {} reason={}", id, reasonCode);
        return attempt(() -> {
            cancelBookingUseCase.handle(new CancelBookingCommand(id, reasonCode));
            return "Booking %s cancelled".formatted(id);
        });
    }

    @Tool(description = "Register money the central office has collected for a booking: a Deposit or a "
            + "Prepayment, with one of the catalog's payment methods. Returns the payment id")
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
