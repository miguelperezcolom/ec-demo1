package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.uidl.interfaces.HttpRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The listing's {@code ?ids=…}: the concrete bookings the assistant asks to show. */
class BookingIdsFilterTest {

    static HttpRequest withState(Map<String, Object> state) {
        HttpRequest request = mock(HttpRequest.class, RETURNS_DEEP_STUBS);
        when(request.runActionRq().componentState()).thenReturn(state);
        return request;
    }

    @Test
    void theIdsFromTheUrlAreAConcreteSetOfBookings() {
        var ids = BookingCrudOrchestrator.ids(withState(Map.of("ids", "4MBZS7, JXD3G6,")));
        assertThat(ids).containsExactly("4MBZS7", "JXD3G6");
        assertThat(BookingCrudOrchestrator.criteria(null, ids).ids()).containsExactly("4MBZS7", "JXD3G6");
    }

    @Test
    void aListFromTheLiveStateCountsToo() {
        assertThat(BookingCrudOrchestrator.ids(withState(Map.of("ids", List.of("4MBZS7"))))).isEqualTo(Set.of("4MBZS7"));
    }

    @Test
    void noIdsIsNoCondition() {
        var state = new HashMap<String, Object>();
        state.put("ids", " ");
        assertThat(BookingCrudOrchestrator.ids(withState(state))).isNull();
        assertThat(BookingCrudOrchestrator.ids(withState(Map.of()))).isNull();
        assertThat(BookingCrudOrchestrator.criteria(null, null)).isNull();
    }

    @Test
    void theIdsNarrowAlongsideTheDeclaredFilters() {
        var filters = new BookingFilters();
        filters.hotel = "MRU01";
        var criteria = BookingCrudOrchestrator.criteria(filters, Set.of("4MBZS7"));
        assertThat(criteria.hotelCode()).isEqualTo("MRU01");
        assertThat(criteria.ids()).containsExactly("4MBZS7");
    }
}
