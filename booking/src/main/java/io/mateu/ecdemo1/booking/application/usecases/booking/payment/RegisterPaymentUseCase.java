package io.mateu.ecdemo1.booking.application.usecases.booking.payment;

import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Payment;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Records money the central office has collected. Returns the payment's id. */
@Service
@RequiredArgsConstructor
public class RegisterPaymentUseCase {

    final BookingRepository repository;
    final CrsCatalog catalog;
    final Clock clock;

    @Transactional
    public String handle(RegisterPaymentCommand command) {
        var method = catalog.paymentMethod(command.methodCode());
        var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
        var payment = new Payment(
                UUID.randomUUID().toString(),
                command.type(),
                method.code(),
                command.amount(),
                command.date() != null ? command.date() : LocalDate.now(clock),
                command.reference());
        booking.registerPayment(payment, clock.instant());
        repository.save(booking);
        return payment.paymentId();
    }

}
