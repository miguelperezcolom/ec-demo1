package io.mateu.ecdemo1.booking.application.usecases.booking.create;

import io.mateu.core.infra.valuegenerators.LocatorValueGenerator;
import io.mateu.ecdemo1.booking.application.out.outbox.Outbox;
import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingTermsFactory;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateBookingUseCase {

    final BookingRepository repository;
    final BookingTermsFactory termsFactory;
    final CrsCatalog catalog;
    final Outbox outbox;
    final LocatorValueGenerator locatorValueGenerator;
    final Clock clock;

    /**
     * The payment-verification process is requested through the outbox too, so it starts only for
     * a booking that was actually saved. Sending it straight to the broker, as this used to, could
     * start it for a booking whose transaction then rolled back.
     */
    @Transactional
    public String handle(CreateBookingCommand command) {
        var hotel = catalog.hotel(command.hotelCode());
        var id = repository.save(Booking.create(
                new BookingId(locatorValueGenerator.generate().toString()),
                hotel.code(),
                hotel.currency(),
                termsFactory.terms(hotel.code(), command.booking()),
                clock.instant())).id();
        outbox.append(Outbox.Destination.Engine, new ProcessCreationRequested(
                "verify-booking-payment",
                "verify-payment-for-" + id,
                List.of(new Variable("bookingId", id))));
        return id;
    }

}
