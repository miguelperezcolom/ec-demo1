package io.mateu.ecdemo1.booking.application.usecases.booking.noshow;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingAudit;
import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.NoShowPolicy;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;

/**
 * «Registrar no-show»'s worker step: the hotel says the guest did not arrive, and the booking is
 * cancelled as a no-show under the CRS's {@link NoShowPolicy}. The engine is answered by the
 * caller, after this transaction has committed: sent from inside it, a broker slow to take the
 * reply would keep the booking's row locked for as long as the reply is retried.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterNoShowUseCase {

    final BookingRepository repository;
    final NoShowPolicy policy;
    final Clock clock;
    final BookingAudit audit;

    @Transactional
    public void handle(String bookingId) {
        var hotel = repository.findById(new BookingId(bookingId)).map(b -> b.getHotelCode()).orElse(null);
        var booking = audit.run("Booking no-show", bookingId, hotel, BookingAudit.params("feePercent", policy.feePercent()),
                () -> {
                    var b = repository.findByIdForUpdate(new BookingId(bookingId))
                            .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));
                    b.noShow(policy, clock.instant());
                    repository.save(b);
                    return b;
                }, b -> "No-show · cuesta " + b.totalAmount());
        log.info("Booking {} is a no-show: it now costs {} ({}% of {})", bookingId, booking.totalAmount(),
                policy.feePercent(), booking.originalAmount());
    }

}
