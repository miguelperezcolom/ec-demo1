package io.mateu.ecdemo1.booking.domain;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCancelled;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingChange;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCreated;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingEvent;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingModified;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Payment;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    Booking created() {
        return Booking.create(new BookingId("B1"), "PMI01", "EUR", Fixtures.terms(3), NOW);
    }

    @Test
    void creationStartsPendingAtVersionOneAndAnnouncesIt() {
        var booking = created();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.Pending);
        assertThat(booking.getVersion()).isEqualTo(1);
        assertThat(booking.popEvents()).singleElement().isInstanceOfSatisfying(BookingCreated.class, e -> {
            assertThat(e.bookingId()).isEqualTo("B1");
            assertThat(e.hotelCode()).isEqualTo("PMI01");
            assertThat(e.version()).isEqualTo(1);
            assertThat(e.partitionKey()).isEqualTo("B1");
        });
    }

    @Test
    void everyChangeBumpsTheVersionByOneAndAnnouncesTheNewOne() {
        var booking = created();
        booking.update(Fixtures.terms(4), NOW);
        booking.registerPayment(payment("P1"), NOW);
        booking.confirm(NOW);
        booking.cancel("CLI", NOW);

        assertThat(booking.popEvents()).map(e -> ((BookingEvent) e).version()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(booking.getVersion()).isEqualTo(5);
    }

    @Test
    void modificationsSayWhatKindOfChangeTheyWere() {
        var booking = created();
        booking.popEvents();
        booking.update(Fixtures.terms(4), NOW);
        booking.registerPayment(payment("P1"), NOW);
        booking.confirm(NOW);

        assertThat(booking.popEvents()).map(e -> ((BookingModified) e).change())
                .containsExactly(BookingChange.TermsUpdated, BookingChange.PaymentRegistered, BookingChange.Confirmed);
    }

    @Test
    void cancellingRecordsTheReason() {
        var booking = created();
        booking.popEvents();
        booking.cancel("CLI", NOW);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.Cancelled);
        assertThat(booking.getCancellation().reasonCode()).isEqualTo("CLI");
        assertThat(booking.popEvents()).singleElement().isInstanceOfSatisfying(BookingCancelled.class,
                e -> assertThat(e.reasonCode()).isEqualTo("CLI"));
    }

    @Test
    void repeatingAConfirmationOrACancellationIsNotAChange() {
        var booking = created();
        booking.confirm(NOW);
        booking.confirm(NOW);
        booking.cancel("CLI", NOW);
        booking.cancel("IMP", NOW);

        assertThat(booking.getVersion()).isEqualTo(3);
        assertThat(booking.getCancellation().reasonCode()).isEqualTo("CLI");
    }

    @Test
    void aCancelledBookingCannotBeModifiedConfirmedOrPaid() {
        var booking = created();
        booking.cancel("CLI", NOW);

        assertThatThrownBy(() -> booking.update(Fixtures.terms(2), NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> booking.confirm(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> booking.registerPayment(payment("P1"), NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPaymentIsRegisteredOnce() {
        var booking = created();
        booking.registerPayment(payment("P1"), NOW);

        assertThatThrownBy(() -> booking.registerPayment(payment("P1"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(booking.paidAmount()).isEqualByComparingTo("100");
    }

    @Test
    void annotatingThePmsReferenceIsNotAChangeToTheBooking() {
        var booking = created();
        booking.popEvents();
        booking.annotatePmsReference("OPERA-123", NOW);

        assertThat(booking.getPmsReference().reservationId()).isEqualTo("OPERA-123");
        assertThat(booking.getVersion()).isEqualTo(1);
        assertThat(booking.popEvents()).isEmpty();
    }

    @Test
    void theTotalIsTheSumOfEveryNightOfEveryRoom() {
        assertThat(created().totalAmount()).isEqualByComparingTo("450.00");
    }

    static Payment payment(String id) {
        return new Payment(id, PaymentType.Deposit, "VISA", new BigDecimal("100"), LocalDate.of(2026, 9, 22), null);
    }
}
