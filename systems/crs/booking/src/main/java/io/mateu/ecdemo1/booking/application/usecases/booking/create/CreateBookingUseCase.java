package io.mateu.ecdemo1.booking.application.usecases.booking.create;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingAudit;
import io.mateu.core.infra.valuegenerators.LocatorValueGenerator;
import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingTermsFactory;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.tracing.Traces;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Makes a booking — confirmed, with the payments collected as it was made — in one transaction, so
 * it reaches whoever integrates with the CRS as one event at version one, and the PMS once.
 */
@Service
@RequiredArgsConstructor
public class CreateBookingUseCase {

    final BookingRepository repository;
    final BookingTermsFactory termsFactory;
    final CrsCatalog catalog;
    final LocatorValueGenerator locatorValueGenerator;
    final Clock clock;
    final Traces traces;
    final BookingAudit audit;

    /**
     * A channel's own reference names one booking: sent again — a front office retrying after a
     * timeout — it answers with the booking already made, whatever the price or the payments say by
     * now. A new one with an expected total is made only at that total.
     */
    @Transactional
    public String handle(CreateBookingCommand command) {
        // The CRS entry of a booking's trace: whatever reaches the PMS for it continues from here, and
        // it is found in Tempo by these attributes.
        return traces.inSpan("booking.create", () -> {
            var params = BookingAudit.params("hotel", command.hotelCode(), "expectedTotal", command.expectedTotal(),
                    "payments", command.payments() == null ? 0 : command.payments().size());
            String id;
            try {
                id = create(command);
            } catch (RuntimeException e) {
                audit.failed("Booking created", null, command.hotelCode(), params, e.getMessage());
                throw e;
            }
            audit.done("Booking created", id, command.hotelCode(), params, "Reserva " + id);
            traces.tag("booking.id", id);
            traces.tag("booking.locator", id);
            traces.tag("hotel.code", command.hotelCode());
            return id;
        });
    }

    private String create(CreateBookingCommand command) {
        var hotel = catalog.hotel(command.hotelCode());
        var terms = termsFactory.terms(hotel.code(), command.booking());
        if (terms.externalReference() != null) {
            var existing = repository.findByChannelReference(hotel.code(), terms.channelCode(), terms.externalReference());
            if (existing.isPresent()) {
                return existing.get().id();
            }
        }
        var payments = command.payments().stream().map(p -> p.toPayment(catalog, hotel.code(), clock)).toList();
        return repository.save(Booking.create(
                new BookingId(locatorValueGenerator.generate().toString()),
                hotel.code(),
                hotel.currency(),
                terms,
                payments,
                command.expectedTotal(),
                clock.instant())).id();
    }

}
