package io.mateu.ecdemo1.booking.domain.aggregates.booking;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCancelled;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingChange;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCreated;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingModified;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingTerms;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Cancellation;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Payment;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PmsReference;
import io.mateu.workflow.ddd.AggregateRoot;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A booking as the CRS knows it — the commercial master of the stay.
 *
 * <p>Every change bumps {@link #version} by one and records one event carrying the new version.
 * Annotating the PMS reference is the exception: it is the integration writing back where the
 * booking landed, not a change to the booking, so it neither versions it nor announces anything —
 * announcing it would only send the booking round the integration again.
 */
@Getter
public class Booking extends AggregateRoot {

    private final BookingId id;
    private final String hotelCode;
    private final String currency;
    private BookingTerms terms;
    private final List<Payment> payments;
    private BookingStatus status;
    private Cancellation cancellation;
    private PmsReference pmsReference;
    private final Instant created;
    private Instant updated;
    private long version;

    public Booking(BookingId id, String hotelCode, String currency, BookingTerms terms,
                   List<Payment> payments, BookingStatus status, Cancellation cancellation,
                   PmsReference pmsReference, Instant created, Instant updated, long version) {
        this.id = id;
        this.hotelCode = hotelCode;
        this.currency = currency;
        this.terms = terms;
        this.payments = new ArrayList<>(payments);
        this.status = status;
        this.cancellation = cancellation;
        this.pmsReference = pmsReference;
        this.created = created;
        this.updated = updated;
        this.version = version;
    }

    public static Booking create(BookingId id, String hotelCode, String currency, BookingTerms terms,
                                 Instant now) {
        var booking = new Booking(id, hotelCode, currency, terms, List.of(), BookingStatus.Pending,
                null, null, now, now, 1);
        booking.send(new BookingCreated(eventId(), id.id(), hotelCode, booking.version, now));
        return booking;
    }

    public void update(BookingTerms terms, Instant now) {
        requireAlive("modified");
        this.terms = terms;
        modified(BookingChange.TermsUpdated, now);
    }

    /** Confirming a confirmed booking is not a change, and records nothing. */
    public void confirm(Instant now) {
        requireAlive("confirmed");
        if (status == BookingStatus.Confirmed) {
            return;
        }
        status = BookingStatus.Confirmed;
        modified(BookingChange.Confirmed, now);
    }

    /** Cancelling a cancelled booking is not a change, and records nothing. */
    public void cancel(String reasonCode, Instant now) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("A cancellation needs a reason");
        }
        if (status == BookingStatus.Cancelled) {
            return;
        }
        status = BookingStatus.Cancelled;
        cancellation = new Cancellation(reasonCode, now);
        bump(now);
        send(new BookingCancelled(eventId(), id.id(), hotelCode, version, now, reasonCode));
    }

    public void registerPayment(Payment payment, Instant now) {
        requireAlive("paid");
        if (payments.stream().anyMatch(p -> p.paymentId().equals(payment.paymentId()))) {
            throw new IllegalArgumentException("Payment %s is already registered".formatted(payment.paymentId()));
        }
        payments.add(payment);
        modified(BookingChange.PaymentRegistered, now);
    }

    public void annotatePmsReference(String reservationId, Instant now) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("A PMS reference needs the reservation id");
        }
        pmsReference = new PmsReference(reservationId, now);
    }

    public List<Payment> getPayments() {
        return List.copyOf(payments);
    }

    /** What the booking costs: its price, or — cancelled with a fee, as a no-show — the fee. */
    public BigDecimal totalAmount() {
        return cancellation != null && cancellation.fee() != null ? cancellation.fee() : terms.total();
    }

    /** The price as it was booked, whatever a cancellation left of it. */
    public BigDecimal originalAmount() {
        return terms.total();
    }

    /** The CRS's cancellation reason for a guest who did not arrive. */
    public static final String NO_SHOW = "NOS";

    /**
     * The guest did not arrive: the hotel says so (HLA F006), and the CRS applies its rule — the
     * booking is cancelled as a no-show and costs a share of its original price. Once: a second
     * notice of the same no-show changes nothing, and a booking already cancelled is not a no-show.
     */
    public void noShow(int feePercent, Instant now) {
        if (feePercent < 0 || feePercent > 100) {
            throw new IllegalArgumentException("A no-show fee is a share of the price, 0 to 100: " + feePercent);
        }
        if (status == BookingStatus.Cancelled) {
            return;
        }
        var fee = terms.total().multiply(BigDecimal.valueOf(feePercent)).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        status = BookingStatus.Cancelled;
        cancellation = new Cancellation(NO_SHOW, now, fee, feePercent);
        bump(now);
        send(new BookingCancelled(eventId(), id.id(), hotelCode, version, now, NO_SHOW));
    }

    public BigDecimal paidAmount() {
        return payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireAlive(String what) {
        if (status == BookingStatus.Cancelled) {
            throw new IllegalStateException("Booking %s is cancelled and cannot be %s".formatted(id.id(), what));
        }
    }

    private void modified(BookingChange change, Instant now) {
        bump(now);
        send(new BookingModified(eventId(), id.id(), hotelCode, version, now, change));
    }

    private void bump(Instant now) {
        version++;
        updated = now;
    }

    private static String eventId() {
        return UUID.randomUUID().toString();
    }
}
