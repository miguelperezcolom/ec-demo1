package io.mateu.ecdemo1.booking.application;

import io.mateu.core.infra.valuegenerators.LocatorValueGenerator;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingAudit;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingTermsFactory;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;
import io.mateu.ecdemo1.booking.tracing.Traces;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Who did what to a booking: the use cases audit themselves — made, and a cancellation refused — once each. */
class BookingAuditTest {

    final CrsCatalog catalog = io.mateu.ecdemo1.booking.infra.out.catalog.ImportedCatalogs.standardCatalog();
    final BookingTermsFactory terms = new BookingTermsFactory(catalog, new RoomPricing());
    final WalkInBookingTest.Store store = new WalkInBookingTest.Store();
    final RecordingTrail trail = new RecordingTrail();
    final BookingAudit audit = new BookingAudit(trail, null);
    final CreateBookingUseCase create = new CreateBookingUseCase(store, terms, catalog, new LocatorValueGenerator(),
            Clock.systemUTC(), Traces.untraced(), audit);
    final CancelBookingUseCase cancel = new CancelBookingUseCase(store, catalog, Clock.systemUTC(), audit);

    @Test
    void aBookingMadeIsAuditedWithItsLocatorHotelAndWho() {
        trail.actor = "luis";
        var id = create.handle(new CreateBookingCommand("MRU01",
                WalkInBookingTest.walkIn(null, WalkInBookingTest.holder(), 2), null, List.of()));

        assertThat(trail.recorded).singleElement().satisfies(r -> {
            assertThat(r.action()).isEqualTo("Booking created");
            assertThat(r.bookingId()).isEqualTo(id);
            assertThat(r.hotelCode()).isEqualTo("MRU01");
            assertThat(r.by()).isEqualTo("luis");
            assertThat(r.succeeded()).isTrue();
            assertThat(r.parameters()).containsEntry("locator", id);
        });
    }

    @Test
    void aRefusedCancellationIsAuditedWithWhyAndTheBookingStays() {
        var id = create.handle(new CreateBookingCommand("MRU01",
                WalkInBookingTest.walkIn(null, WalkInBookingTest.holder(), 2), null, List.of()));
        trail.recorded.clear();

        assertThatThrownBy(() -> cancel.handle(new CancelBookingCommand(id, "NO-SUCH-REASON")))
                .isInstanceOf(RuntimeException.class);

        assertThat(trail.recorded).singleElement().satisfies(r -> {
            assertThat(r.action()).isEqualTo("Booking cancelled");
            assertThat(r.bookingId()).isEqualTo(id);
            assertThat(r.succeeded()).isFalse();
            assertThat(r.parameters()).containsEntry("reason", "NO-SUCH-REASON");
        });
    }

    @Test
    void aBookingThatIsNotThereIsAFailureToo() {
        assertThatThrownBy(() -> cancel.handle(new CancelBookingCommand("NOPE", "CLI")))
                .isInstanceOf(NoSuchElementException.class);

        assertThat(trail.recorded).singleElement().satisfies(r -> {
            assertThat(r.succeeded()).isFalse();
            assertThat(r.response()).contains("NOPE");
        });
    }
}
