package io.mateu.ecdemo1.mdm.marking;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.footprint.Footprint;
import io.mateu.ecdemo1.mdm.store.Customer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Which «Solo nombre» contacts are anonymised: only on certainty, and only past the retention period. */
class NameOnlyCleanupTest {

    static final Instant NOW = Instant.parse("2026-11-30T03:30:00Z");
    static final Duration AFTER = Duration.ofDays(30);
    static final Instant CUTOFF = NOW.minus(AFTER);
    static final Instant LONG_AGO = NOW.minus(Duration.ofDays(45));

    static Customer nameOnly() {
        var c = new Customer();
        c.id = "C-1";
        c.status = CustomerStatus.PROVISIONAL;
        c.firstName = "Tomas";
        c.lastName = "Serra";
        c.createdAt = LONG_AGO;
        c.updatedAt = LONG_AGO;
        return c;
    }

    static Optional<Footprint.Booking> booking(String id, String status, String reason) {
        return Optional.of(new Footprint.Booking(id, "MRU01", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 12),
                status, "Tomas Serra", null, "CALLCENTER", null, reason));
    }

    static NameOnlyCleanup.Decision decide(Customer c, Instant lastSeen, List<Optional<Footprint.Booking>> bookings) {
        return NameOnlyCleanup.decide(c, lastSeen, bookings, CUTOFF, AFTER);
    }

    @Test
    void aNameWithOnlyCancelledAndNoShowBookings_pastTheRetention_isAnonymised_andTheReasonKept() {
        var d = decide(nameOnly(), LONG_AGO, List.of(booking("BJRQ5D", "Cancelled", "NOS"), booking("BK2", "Cancelled", "OTR")));
        assertThat(d.anonymize()).isTrue();
        assertThat(d.reason()).contains("BJRQ5D no-show").contains("BK2 cancelada").contains("30 días").contains("RGPD");
    }

    @Test
    void contactDataKeepsIt() {
        var c = nameOnly();
        c.email = "tomas@example.com";
        assertThat(decide(c, LONG_AGO, List.of(booking("B1", "Cancelled", "NOS"))).anonymize()).isFalse();
    }

    @Test
    void aBookingThatIsNotCancelledKeepsIt() {
        assertThat(decide(nameOnly(), LONG_AGO, List.of(booking("B1", "Cancelled", "NOS"), booking("B2", "Confirmed", null)))
                .anonymize()).isFalse();
    }

    @Test
    void recentActivityKeepsIt() {
        assertThat(decide(nameOnly(), NOW.minus(Duration.ofDays(3)), List.of(booking("B1", "Cancelled", "NOS"))).anonymize()).isFalse();
        var updated = nameOnly();
        updated.updatedAt = NOW.minus(Duration.ofDays(1));
        assertThat(decide(updated, LONG_AGO, List.of(booking("B1", "Cancelled", "NOS"))).anonymize()).isFalse();
    }

    @Test
    void doubtKeepsIt_noBookings_orOneTheCrsDoesNotHave() {
        assertThat(decide(nameOnly(), LONG_AGO, List.of()).anonymize()).isFalse();
        assertThat(decide(nameOnly(), LONG_AGO, List.of(booking("B1", "Cancelled", "NOS"), Optional.empty())).anonymize()).isFalse();
    }

    @Test
    void anAnonymisedOneIsNotAnonymisedTwice() {
        var c = nameOnly();
        c.anonymizedAt = LONG_AGO;
        assertThat(decide(c, LONG_AGO, List.of(booking("B1", "Cancelled", "NOS"))).anonymize()).isFalse();
    }

    @Test
    void aNoShowIsACancellation() {
        assertThat(booking("B", "NoShow", null).get().cancelledOrNoShow()).isTrue();
        assertThat(booking("B", "No-Show", null).get().cancelledOrNoShow()).isTrue();
        assertThat(booking("B", "Canceled", null).get().cancelledOrNoShow()).isTrue();
        assertThat(booking("B", "Confirmed", null).get().cancelledOrNoShow()).isFalse();
    }
}
