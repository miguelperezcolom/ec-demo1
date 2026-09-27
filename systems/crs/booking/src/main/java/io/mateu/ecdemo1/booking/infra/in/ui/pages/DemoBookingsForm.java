package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.out.partners.PartnerDirectory;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;
import io.mateu.uidl.data.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Creates ten demo bookings for MRU01 — the bookings list asks first, since the hotel may be
 * integrated with Opera and each one then lands in a real PMS. The bookings are made with
 * {@link DemoBookingGenerator} and created through the same use cases as the wizard's, one at a time:
 * one the CRS refuses is reported and does not stop the rest.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class DemoBookingsForm {

    static final int COUNT = 10;
    static final String OPERA_WARNING =
            "Si MRU01 tiene la integración activa, cada reserva se escribirá también en Opera (XMAR).";

    final CreateBookingUseCase createBookingUseCase;
    final CrsCatalog catalog;
    final RoomPricing pricing;
    final PartnerDirectory partnerDirectory;
    final Clock clock;

    /** A different batch each time; a seed gives the same bookings on the same day (tests). */
    Outcome create() {
        return create(null);
    }

    Outcome create(Long seed) {
        var notes = new ArrayList<String>();
        List<PartnerDirectory.TradingPartner> partners;
        try {
            partners = partnerDirectory.activePartners();
        } catch (RuntimeException e) {
            partners = List.of();
            notes.add("The partners could not be read (%s): the bookings are all direct".formatted(e.getMessage()));
        }
        var batch = Long.toString(clock.instant().getEpochSecond(), 36).toUpperCase(Locale.ROOT);
        var generator = new DemoBookingGenerator(catalog, pricing, partners,
                seed != null ? new Random(seed) : new Random(), LocalDate.now(clock), batch);
        var created = new ArrayList<String>();
        var failed = new ArrayList<String>();
        var bookings = generator.generate(COUNT);
        for (int i = 0; i < bookings.size(); i++) {
            var booking = bookings.get(i);
            try {
                created.add(createBookingUseCase.handle(new CreateBookingCommand(booking.hotelCode(), booking.request(),
                        null, BookingRequests.newPayments(booking.payments()))));
            } catch (RuntimeException e) {
                failed.add("#%d %s (%s)".formatted(i + 1, booking.request().holder().fullName(), e.getMessage()));
            }
        }
        return new Outcome(created, failed, notes);
    }

    record Outcome(List<String> created, List<String> failed, List<String> notes) {

        Message message() {
            var parts = new ArrayList<String>();
            parts.add("%d of %d demo bookings created".formatted(created.size(), created.size() + failed.size()));
            if (!failed.isEmpty()) {
                parts.add("Not created: " + String.join("; ", failed));
            }
            parts.addAll(notes);
            var text = String.join(". ", parts);
            return failed.isEmpty() && notes.isEmpty() ? Message.success(text)
                    : created.isEmpty() ? Message.error(text) : Message.warning(text);
        }
    }
}
