package io.mateu.ecdemo1.booking.application.usecases.booking.payment;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingAudit;
import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.application.usecases.booking.PaymentRequest;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;

/**
 * Records money the central office has collected on a booking that already exists. Returns the
 * payment's id. The payments collected as a booking is made go with its creation instead.
 */
@Service
@RequiredArgsConstructor
public class RegisterPaymentUseCase {

    final BookingRepository repository;
    final CrsCatalog catalog;
    final Clock clock;
    final BookingAudit audit;

    @Transactional
    public String handle(RegisterPaymentCommand command) {
        var hotel = repository.findById(new BookingId(command.id())).map(b -> b.getHotelCode()).orElse(null);
        return audit.run("Payment registered", command.id(), hotel, BookingAudit.params("type", command.type(),
                "method", command.methodCode(), "amount", command.amount()), () -> {
                    var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                            .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
                    var payment = new PaymentRequest(command.type(), command.methodCode(), command.amount(), command.date(),
                            command.reference()).toPayment(catalog, booking.getHotelCode(), clock);
                    booking.registerPayment(payment, clock.instant());
                    repository.save(booking);
                    return payment.paymentId();
                }, id -> "Cobro " + id);
    }

}
