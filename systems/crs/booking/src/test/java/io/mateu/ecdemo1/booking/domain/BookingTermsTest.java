package io.mateu.ecdemo1.booking.domain;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookedRoom;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingTerms;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Guest;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.GuestType;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.NightlyRate;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Stay;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTermsTest {

    static final Stay STAY = new Stay(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));

    @Test
    void departureMustBeAfterArrival() {
        assertThatThrownBy(() -> new Stay(Fixtures.MONDAY, Fixtures.MONDAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyRoomNeedsExactlyOneRatePerNight() {
        var oneNightShort = new BookedRoom(1, "DBL", "BAR", "AD", 2, List.of(), List.of(),
                List.of(new NightlyRate(Fixtures.MONDAY, BigDecimal.TEN)));

        assertThatThrownBy(() -> new BookingTerms("WEB", null, null, STAY, Fixtures.holder(), List.of(oneNightShort), null))
                .hasMessageContaining("exactly one rate per night");
    }

    @Test
    void roomLinesCannotRepeat() {
        assertThatThrownBy(() -> new BookingTerms("WEB", null, null, STAY, Fixtures.holder(),
                List.of(Fixtures.room(1, STAY), Fixtures.room(1, STAY)), null))
                .hasMessageContaining("repeated");
    }

    @Test
    void aBookingNeedsARoom() {
        assertThatThrownBy(() -> new BookingTerms("WEB", null, null, STAY, Fixtures.holder(), List.of(), null))
                .hasMessageContaining("at least one room");
    }

    @Test
    void aRoomCannotHaveMoreGuestsThanItsOccupancy() {
        var three = List.of(adult("A"), adult("B"), adult("C"));

        assertThatThrownBy(() -> new BookedRoom(1, "DBL", "BAR", "AD", 2, List.of(), three,
                List.of(new NightlyRate(Fixtures.MONDAY, BigDecimal.TEN))))
                .hasMessageContaining("more guests than its occupancy");
    }

    @Test
    void aChildNeedsAnAge() {
        assertThatThrownBy(() -> new Guest("Leo", "García", GuestType.Child, null, null, null, null, null))
                .hasMessageContaining("needs an age");
    }

    static Guest adult(String name) {
        return new Guest(name, "García", GuestType.Adult, null, null, null, null, null);
    }
}
