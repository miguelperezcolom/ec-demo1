package io.mateu.ecdemo1.booking.domain;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookedRoom;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingTerms;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.NightlyRate;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Stay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Fixtures {

    /** A Monday, so the first four nights are weekdays. */
    public static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    private Fixtures() {
    }

    public static Holder holder() {
        return new Holder("Ana", "García", "ana@example.com", "+34600000000", "ES");
    }

    public static BookingTerms terms(int nights) {
        var stay = new Stay(MONDAY, MONDAY.plusDays(nights));
        return new BookingTerms("WEB", null, null, stay, holder(), List.of(room(1, stay)), null);
    }

    public static BookedRoom room(int line, Stay stay) {
        return new BookedRoom(line, "DBL", "BAR", "AD", 2, List.of(), List.of(),
                stay.nightDates().stream().map(d -> new NightlyRate(d, new BigDecimal("150.00"))).toList());
    }
}
