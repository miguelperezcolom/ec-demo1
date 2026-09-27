package io.mateu.ecdemo1.booking.application;

import io.mateu.core.infra.valuegenerators.LocatorValueGenerator;
import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingTermsFactory;
import io.mateu.ecdemo1.booking.application.usecases.booking.RoomRequest;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.quote.QuoteBookingUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A walk-in, as the hotel's front office makes it: quoted first, then booked at the quoted price, once. */
class WalkInBookingTest {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    final CrsCatalog catalog = CrsCatalog.standard();
    final BookingTermsFactory terms = new BookingTermsFactory(catalog, new RoomPricing());
    final Store store = new Store();
    final CreateBookingUseCase create = new CreateBookingUseCase(store, terms, catalog, (d, e) -> { },
            new LocatorValueGenerator(), Clock.systemUTC());
    final QuoteBookingUseCase quote = new QuoteBookingUseCase(catalog, terms);

    static BookingRequest walkIn(String reference, Holder holder, int adults) {
        return new BookingRequest("WALKIN", null, reference, TODAY, TODAY.plusDays(3), holder,
                List.of(new RoomRequest("STD-KING", "DIRECTA", "DESAYUNO", adults, List.of(), List.of())), null);
    }

    static Holder holder() {
        return new Holder("Nora", "Vega", "nora.vega@example.com", "+34 600 111 222", "ES");
    }

    @Test
    void aQuoteNeedsNoHolderAndPricesAsTheBookingWill() {
        var q = quote.handle("MRU01", walkIn(null, null, 2));

        assertThat(q.nights()).isEqualTo(3);
        assertThat(q.channelCode()).isEqualTo("WALKIN");
        assertThat(q.rooms()).singleElement().satisfies(r -> {
            assertThat(r.nightlyRates()).hasSize(3);
            assertThat(r.roomTypeName()).isNotBlank();
            assertThat(r.boardName()).isNotBlank();
        });
        var booked = terms.terms("MRU01", walkIn("FO-1", holder(), 2));
        assertThat(q.total()).isEqualByComparingTo(booked.total());
        assertThat(store.saved).isEmpty();
    }

    @Test
    void aQuoteRefusesAnUnknownCodeAndPeopleThatDoNotFit() {
        var unknown = new BookingRequest("WALKIN", null, null, TODAY, TODAY.plusDays(1), null,
                List.of(new RoomRequest("NOPE", "DIRECTA", "DESAYUNO", 1, List.of(), List.of())), null);
        assertThatThrownBy(() -> quote.handle("MRU01", unknown))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NOPE");
        assertThatThrownBy(() -> quote.handle("MRU01", walkIn(null, null, 9)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at most");
    }

    @Test
    void bookedAtTheQuotedPriceAndOnceForTheSameReference() {
        var total = quote.handle("MRU01", walkIn(null, null, 2)).total();

        var first = create.handle(new CreateBookingCommand("MRU01", walkIn("FO-ABC123", holder(), 2), total));
        var again = create.handle(new CreateBookingCommand("MRU01", walkIn("FO-ABC123", holder(), 2), total));

        assertThat(again).isEqualTo(first);
        assertThat(store.saved).hasSize(1);
    }

    @Test
    void aPriceThatChangedSinceTheQuoteIsRefused() {
        assertThatThrownBy(() -> create.handle(
                new CreateBookingCommand("MRU01", walkIn("FO-XYZ", holder(), 2), new BigDecimal("1.00"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("price changed");
        assertThat(store.saved).isEmpty();
    }

    static class Store implements BookingRepository {
        final Map<String, Booking> saved = new HashMap<>();
        final List<String[]> references = new ArrayList<>();

        @Override
        public Optional<Booking> findByIdForUpdate(BookingId id) {
            return findById(id);
        }

        @Override
        public Optional<BookingId> findByChannelReference(String hotelCode, String channelCode, String externalReference) {
            return references.stream().filter(r -> r[0].equals(hotelCode) && r[1].equals(channelCode) && r[2].equals(externalReference))
                    .map(r -> new BookingId(r[3])).findFirst();
        }

        @Override
        public Optional<Booking> findById(BookingId id) {
            return Optional.ofNullable(saved.get(id.id()));
        }

        @Override
        public BookingId save(Booking booking) {
            saved.put(booking.getId().id(), booking);
            var t = booking.getTerms();
            if (t.externalReference() != null) {
                references.add(new String[]{booking.getHotelCode(), t.channelCode(), t.externalReference(), booking.getId().id()});
            }
            return booking.getId();
        }

        @Override
        public void deleteAllById(List<BookingId> ids) {
            ids.forEach(id -> saved.remove(id.id()));
        }
    }
}
