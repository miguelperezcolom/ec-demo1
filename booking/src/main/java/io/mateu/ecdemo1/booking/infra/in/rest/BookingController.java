package io.mateu.ecdemo1.booking.infra.in.rest;

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
import io.mateu.ecdemo1.booking.application.usecases.booking.pmsreference.AnnotatePmsReferenceCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.pmsreference.AnnotatePmsReferenceUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.update.UpdateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.update.UpdateBookingUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The CRS's booking API. {@code GET /bookings/{id}} is also how the integration reads a booking
 * again after an event says it changed.
 */
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    final BookingQueryService queryService;
    final CreateBookingUseCase createBookingUseCase;
    final UpdateBookingUseCase updateBookingUseCase;
    final ConfirmBookingUseCase confirmBookingUseCase;
    final CancelBookingUseCase cancelBookingUseCase;
    final RegisterPaymentUseCase registerPaymentUseCase;
    final AnnotatePmsReferenceUseCase annotatePmsReferenceUseCase;
    final java.time.Clock clock;

    public record Created(String id) {
    }

    public record CancelRequest(String reasonCode) {
    }

    public record PaymentRequest(PaymentType type, String methodCode, BigDecimal amount, LocalDate date,
                                 String reference) {
    }

    public record PaymentRegistered(String paymentId) {
    }

    public record PmsReferenceRequest(String reservationId) {
    }

    @GetMapping
    @Operation(summary = "List bookings, most recent first")
    public List<BookingDto> list(@RequestParam(required = false) String search,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return queryService.list(search, page, size);
    }

    @GetMapping("/future")
    @Operation(summary = "A hotel's bookings still to arrive, not cancelled, by arrival: a page after the given position")
    public List<BookingDto> future(@RequestParam String hotelCode,
                                   @RequestParam(required = false) LocalDate from,
                                   @RequestParam(required = false) LocalDate afterArrival,
                                   @RequestParam(required = false) String afterId,
                                   @RequestParam(defaultValue = "50") int limit) {
        return queryService.future(hotelCode, from == null ? LocalDate.now(clock) : from, afterArrival, afterId,
                Math.min(limit, 500));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read a booking: stay, rooms with their guests and nightly rates, payments, status and version")
    public BookingDto get(@PathVariable String id) {
        return queryService.getById(id).orElseThrow(() -> new NoSuchElementException("Booking not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking in a hotel. The CRS prices each room night by night")
    public Created create(@RequestBody CreateBookingCommand command) {
        return new Created(createBookingUseCase.handle(command));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modify a booking: replaces its terms as a whole and prices it again")
    public void update(@PathVariable String id, @RequestBody BookingRequest booking) {
        updateBookingUseCase.handle(new UpdateBookingCommand(id, booking));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a pending booking")
    public void confirm(@PathVariable String id) {
        confirmBookingUseCase.handle(new ConfirmBookingCommand(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking with one of the catalog's cancellation reasons")
    public void cancel(@PathVariable String id, @RequestBody CancelRequest request) {
        cancelBookingUseCase.handle(new CancelBookingCommand(id, request.reasonCode()));
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register money the central office has collected for the booking")
    public PaymentRegistered registerPayment(@PathVariable String id, @RequestBody PaymentRequest request) {
        return new PaymentRegistered(registerPaymentUseCase.handle(new RegisterPaymentCommand(
                id, request.type(), request.methodCode(), request.amount(), request.date(), request.reference())));
    }

    @PutMapping("/{id}/pms-reference")
    @Operation(summary = "Record where the booking lives in the PMS. Not a change to the booking: no event, no new version")
    public void annotatePmsReference(@PathVariable String id, @RequestBody PmsReferenceRequest request) {
        annotatePmsReferenceUseCase.handle(new AnnotatePmsReferenceCommand(id, request.reservationId()));
    }
}
