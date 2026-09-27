package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.out.partners.PartnerDirectory;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentUseCase;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.Dialog;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.ModelViewComponent;
import io.mateu.uidl.data.UICommand;
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
 * Creates ten demo bookings for MRU01, after asking: the hotel is integrated with Opera, so each one
 * may land in a real PMS. Opened in a dialog from the bookings list. The bookings are made with
 * {@link DemoBookingGenerator} and created through the same use cases as the wizard's, one at a time:
 * one the CRS refuses is reported and does not stop the rest.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Demo bookings")
public class DemoBookingsForm {

    static final int COUNT = 10;
    static final String OPERA_WARNING =
            "Si MRU01 tiene la integración activa, cada reserva se escribirá también en Opera (XMAR).";

    @ReadOnly
    @Label("What")
    String what;

    @ReadOnly
    @Label("Opera")
    String warning;

    /** The same seed on the same day gives the same bookings; empty, a different batch each time. */
    @Label("Seed (optional)")
    Long seed;

    final CreateBookingUseCase createBookingUseCase;
    final RegisterPaymentUseCase registerPaymentUseCase;
    final CrsCatalog catalog;
    final RoomPricing pricing;
    final PartnerDirectory partnerDirectory;
    final Clock clock;

    Dialog dialog() {
        what = "%d bookings for %s — %s, arriving in the next 2 to 8 weeks, some through tour operators and online agencies"
                .formatted(COUNT, DemoBookingGenerator.HOTEL, catalog.hotel(DemoBookingGenerator.HOTEL).name());
        warning = OPERA_WARNING;
        return Dialog.builder()
                .headerTitle("+ %d reservas demo".formatted(COUNT))
                .width("34rem")
                .content(new ModelViewComponent(this))
                .build();
    }

    @Toolbar
    @Label("Create them")
    public Object createThem() {
        var outcome = create();
        return List.of(outcome.message(), UICommand.closeModal(),
                UICommand.navigateTo(BookingCrudOrchestrator.LIST_ROUTE));
    }

    @Toolbar
    @Label("Not now")
    public UICommand notNow() {
        return UICommand.closeModal();
    }

    Outcome create() {
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
            String id;
            try {
                id = createBookingUseCase.handle(new CreateBookingCommand(booking.hotelCode(), booking.request()));
                created.add(id);
            } catch (RuntimeException e) {
                failed.add("#%d %s (%s)".formatted(i + 1, booking.request().holder().fullName(), e.getMessage()));
                continue;
            }
            try {
                BookingRequests.registerNewPayments(registerPaymentUseCase, id, booking.payments());
            } catch (RuntimeException e) {
                notes.add("%s was created without its payment (%s)".formatted(id, e.getMessage()));
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
