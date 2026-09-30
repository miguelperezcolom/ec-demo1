package io.mateu.ecdemo1.booking.domain;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.NoShowPolicy;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCancelled;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingChange;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCreated;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingEvent;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingModified;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookedRoom;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingTerms;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.NightlyRate;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Payment;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    Booking created() {
        return Booking.create(new BookingId("B1"), "PMI01", "EUR", Fixtures.terms(3), NOW);
    }

    /** A booking stored pending, before bookings were born confirmed. */
    Booking pending() {
        return new Booking(new BookingId("B0"), "PMI01", "EUR", Fixtures.terms(3), List.of(), BookingStatus.Pending,
                null, null, NOW, NOW, 1);
    }

    @Test
    void creationIsConfirmedAtVersionOneAndAnnouncesIt() {
        var booking = created();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.Confirmed);
        assertThat(booking.getVersion()).isEqualTo(1);
        assertThat(booking.popEvents()).singleElement().isInstanceOfSatisfying(BookingCreated.class, e -> {
            assertThat(e.bookingId()).isEqualTo("B1");
            assertThat(e.hotelCode()).isEqualTo("PMI01");
            assertThat(e.version()).isEqualTo(1);
            assertThat(e.partitionKey()).isEqualTo("B1");
        });
    }

    @Test
    void thePaymentsMadeWithTheBookingArePartOfItsCreation() {
        var booking = Booking.create(new BookingId("B1"), "PMI01", "EUR", Fixtures.terms(3),
                List.of(payment("P1"), payment("P2")), null, NOW);

        assertThat(booking.paidAmount()).isEqualByComparingTo("200");
        assertThat(booking.getVersion()).isEqualTo(1);
        assertThat(booking.popEvents()).singleElement().isInstanceOf(BookingCreated.class);
    }

    @Test
    void aBookingIsNotCreatedTwiceWithTheSamePayment() {
        assertThatThrownBy(() -> Booking.create(new BookingId("B1"), "PMI01", "EUR", Fixtures.terms(3),
                List.of(payment("P1"), payment("P1")), null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBookingIsMadeAtTheQuotedPriceOrNotAtAll() {
        var total = Fixtures.terms(3).total();

        assertThat(Booking.create(new BookingId("B1"), "PMI01", "EUR", Fixtures.terms(3), List.of(), total, NOW)
                .totalAmount()).isEqualByComparingTo(total);
        assertThatThrownBy(() -> Booking.create(new BookingId("B1"), "PMI01", "EUR", Fixtures.terms(3), List.of(),
                new BigDecimal("1.00"), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The price changed: quoted 1.00 EUR, the CRS prices it at %s EUR now", total.toPlainString());
    }

    @Test
    void everyChangeBumpsTheVersionByOneAndAnnouncesTheNewOne() {
        var booking = created();
        booking.update(Fixtures.terms(4), NOW);
        booking.registerPayment(payment("P1"), NOW);
        booking.cancel("CLI", NOW);

        assertThat(booking.popEvents()).map(e -> ((BookingEvent) e).version()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(booking.getVersion()).isEqualTo(4);
    }

    @Test
    void theSameTermsAreNotAModificationEvenSpelledDifferently() {
        var booking = created();
        booking.popEvents();
        var same = Fixtures.terms(3);
        // what a form sends back: 150 instead of 150.00, a blank comment instead of none
        var room = same.rooms().getFirst();
        var respelled = new BookingTerms(same.channelCode(), "", " ", same.stay(), same.holder(),
                java.util.List.of(new BookedRoom(room.line(), room.roomTypeCode(), room.ratePlanCode(), room.boardCode(),
                        room.adults(), room.childrenAges(), room.guests(), room.nightlyRates().stream()
                        .map(r -> new NightlyRate(r.date(), new java.math.BigDecimal("150"))).toList())), "");

        assertThat(booking.update(respelled, NOW)).isFalse();
        assertThat(booking.popEvents()).isEmpty();
        assertThat(booking.getVersion()).isEqualTo(1);
        assertThat(booking.update(Fixtures.terms(4), NOW)).isTrue();
        assertThat(booking.getVersion()).isEqualTo(2);
    }

    @Test
    void aPendingBookingIsConfirmedAsAChange() {
        var booking = pending();
        booking.confirm(NOW);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.Confirmed);
        assertThat(booking.popEvents()).singleElement().isInstanceOfSatisfying(BookingModified.class, e -> {
            assertThat(e.version()).isEqualTo(2);
            assertThat(e.change()).isEqualTo(BookingChange.Confirmed);
        });
    }

    @Test
    void modificationsSayWhatKindOfChangeTheyWere() {
        var booking = pending();
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
    void aNoShowCancelsTheBookingWhichThenCostsItsFee() {
        var booking = created();
        var original = booking.totalAmount();
        booking.popEvents();

        booking.noShow(new NoShowPolicy(25), NOW);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.Cancelled);
        assertThat(booking.getCancellation().reasonCode()).isEqualTo(Booking.NO_SHOW);
        assertThat(booking.getCancellation().feePercent()).isEqualTo(25);
        assertThat(booking.totalAmount()).isEqualByComparingTo(original.multiply(java.math.BigDecimal.valueOf(0.25)));
        assertThat(booking.originalAmount()).isEqualByComparingTo(original);
        assertThat(booking.popEvents()).singleElement().isInstanceOfSatisfying(BookingCancelled.class,
                e -> assertThat(e.reasonCode()).isEqualTo("NOS"));

        // Told twice, one no-show: no second fee, no second event.
        var version = booking.getVersion();
        booking.noShow(new NoShowPolicy(25), NOW);
        assertThat(booking.getVersion()).isEqualTo(version);
        assertThat(booking.popEvents()).isEmpty();
    }

    @Test
    void aNoShowFeeIsAShareOfThePrice() {
        assertThatThrownBy(() -> new NoShowPolicy(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NoShowPolicy(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new NoShowPolicy(25).feeFor(new BigDecimal("450.00"))).isEqualByComparingTo("112.50");
    }

    @Test
    void repeatingAConfirmationOrACancellationIsNotAChange() {
        var booking = pending();
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
    void aBookingInThePmsCannotBeDeleted() {
        var booking = created();
        booking.requireDeletable();

        booking.annotatePmsReference("OPERA-123", NOW);

        assertThatThrownBy(booking::requireDeletable).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancel it instead");
    }

    @Test
    void theTotalIsTheSumOfEveryNightOfEveryRoom() {
        assertThat(created().totalAmount()).isEqualByComparingTo("450.00");
    }

    static Payment payment(String id) {
        return new Payment(id, PaymentType.Deposit, "VISA", new BigDecimal("100"), LocalDate.of(2026, 9, 22), null);
    }
}
