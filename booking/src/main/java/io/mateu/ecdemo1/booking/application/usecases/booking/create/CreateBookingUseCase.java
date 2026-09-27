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
    /**
     * A channel's own reference names one booking: sent again — a front office retrying after a
     * timeout — it answers with the booking already made, whatever the price says by now. A new one
     * with an expected total is made only at that total.
     */
    @Transactional
    public String handle(CreateBookingCommand command) {
        var hotel = catalog.hotel(command.hotelCode());
        var terms = termsFactory.terms(hotel.code(), command.booking());
        if (terms.externalReference() != null) {
            var existing = repository.findByChannelReference(hotel.code(), terms.channelCode(), terms.externalReference());
            if (existing.isPresent()) {
                return existing.get().id();
            }
        }
        if (command.expectedTotal() != null && terms.total().compareTo(command.expectedTotal()) != 0) {
            throw new IllegalStateException("The price changed: quoted %s %s, the CRS prices it at %s %s now"
                    .formatted(command.expectedTotal().toPlainString(), hotel.currency(), terms.total().toPlainString(),
                            hotel.currency()));
        }
        var id = repository.save(Booking.create(
                new BookingId(locatorValueGenerator.generate().toString()),
                hotel.code(),
                hotel.currency(),
                terms,
                clock.instant())).id();
        outbox.append(Outbox.Destination.Engine, new ProcessCreationRequested(
                "verify-booking-payment",
                "verify-payment-for-" + id,
                List.of(new Variable("bookingId", id))));
        return id;
    }

}
