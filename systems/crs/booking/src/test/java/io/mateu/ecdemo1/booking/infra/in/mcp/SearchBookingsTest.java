package io.mateu.ecdemo1.booking.infra.in.mcp;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingCriteria;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookedRoom;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Guest;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.GuestType;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.NightlyRate;
import io.mateu.ecdemo1.booking.infra.out.catalog.ImportedCatalogs;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** searchBookings: one call answers what would otherwise be a getBooking per booking. */
class SearchBookingsTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    final BookingQueryService queries = mock(BookingQueryService.class);
    final BookingMcpTools tools = new BookingMcpTools(queries, null, null, null, null, null,
            ImportedCatalogs.standardCatalog());

    @Test
    void theDoublesOfSpaniardsArrivingTodayInOneCall() {
        var spanishDouble = booking("A1", "ES", room("DBL-GARDEN"));
        var germanDouble = booking("A2", "DE", room("DBL-POOL"));
        var spanishSuite = booking("A3", "ES", room("JS-STD"));
        var spanishGuestInADouble = booking("A4", "FR", room("DBL-SIDESEA", guest("ES")));
        stored(spanishDouble, germanDouble, spanishSuite, spanishGuestInADouble);

        var found = tools.searchBookings("MRU01", List.of("confirmed"), TODAY, TODAY, null, null, null, null,
                "doble", null, null, "es", null, null, null);

        assertThat(found).extracting(BookingMcpTools.BookingFound::id).containsExactly("A1", "A4");
        assertThat(found.get(0).holderNationality()).isEqualTo("ES");
        assertThat(found.get(0).rooms()).singleElement().satisfies(r -> {
            assertThat(r.roomType()).isEqualTo("DBL-GARDEN");
            assertThat(r.roomTypeName()).startsWith("Doble");
        });
        assertThat(found.get(1).rooms().get(0).guestNationalities()).containsExactly("ES");

        // What the database can filter, it filters.
        var criteria = ArgumentCaptor.forClass(BookingCriteria.class);
        verify(queries).findAll(eq(null), criteria.capture(), any(Pageable.class));
        assertThat(criteria.getValue().hotelCode()).isEqualTo("MRU01");
        assertThat(criteria.getValue().statuses()).isEqualTo(Set.of(BookingStatus.Confirmed));
        assertThat(criteria.getValue().arrivalFrom()).isEqualTo(TODAY);
        assertThat(criteria.getValue().arrivalTo()).isEqualTo(TODAY);
    }

    @Test
    void onlyTheHoldersNationalityWhenAskedSo() {
        stored(booking("A4", "FR", room("DBL-SIDESEA", guest("ES"))));

        assertThat(tools.searchBookings(null, null, null, null, null, null, null, null, null, null, null, "ES", true,
                null, null)).isEmpty();
    }

    @Test
    void theChannelAndTheLimit() {
        stored(booking("A1", "ES", room("DBL-GARDEN")), booking("A2", "ES", room("DBL-GARDEN")),
                booking("A3", "ES", room("DBL-GARDEN")));

        assertThat(tools.searchBookings(null, null, null, null, null, null, "web", null, null, null, null, null, null,
                null, 2)).extracting(BookingMcpTools.BookingFound::id).containsExactly("A1", "A2");
        assertThat(tools.searchBookings(null, null, null, null, null, null, "OTA", null, null, null, null, null, null,
                null, null)).isEmpty();
    }

    void stored(BookingDto... bookings) {
        when(queries.findAll(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(bookings)));
    }

    static BookingDto booking(String id, String holderNationality, BookedRoom room) {
        return new BookingDto(id, "MRU01", "EUR", BookingStatus.Confirmed, 1, "WEB", null, null, TODAY,
                TODAY.plusDays(2), 2, new Holder("Ana", "Pax " + id, null, null, holderNationality), List.of(room),
                List.of(), new BigDecimal("340.00"), BigDecimal.ZERO, null, null, null, null, null, null);
    }

    static BookedRoom room(String roomType, Guest... guests) {
        return new BookedRoom(1, roomType, "BAR", "AD", 2, List.of(), List.of(guests),
                List.of(new NightlyRate(TODAY, new BigDecimal("170.00")),
                        new NightlyRate(TODAY.plusDays(1), new BigDecimal("170.00"))));
    }

    static Guest guest(String nationality) {
        return new Guest("Luis", "Pérez", GuestType.Adult, null, null, nationality, null, null);
    }
}
