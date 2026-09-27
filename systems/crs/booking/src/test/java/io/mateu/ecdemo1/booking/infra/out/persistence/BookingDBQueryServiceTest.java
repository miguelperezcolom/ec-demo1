package io.mateu.ecdemo1.booking.infra.out.persistence;

import io.mateu.ecdemo1.booking.application.out.query.dto.BookingCriteria;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The bookings' query service: the page and the search the database is asked for. */
class BookingDBQueryServiceTest {

    final BookingEntityRepository repository = mock(BookingEntityRepository.class);
    final BookingDBQueryService queries = new BookingDBQueryService(repository);

    @SuppressWarnings("unchecked")
    Pageable pageAsked(Pageable pageable) {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findAll(any(Specification.class), captor.capture())).thenReturn(Page.empty());
        queries.findAll("", null, pageable);
        return captor.getValue();
    }

    @Test
    void bookingsAreReadAPageAtATimeNewestFirstUnlessAnotherOrderIsAsked() {
        var asked = pageAsked(PageRequest.of(4, 20));
        assertThat(asked.getPageNumber()).isEqualTo(4);
        assertThat(asked.getPageSize()).isEqualTo(20);
        assertThat(asked.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "created"));
        assertThat(pageAsked(PageRequest.of(0, 20, Sort.by("arrival"))).getSort()).isEqualTo(Sort.by("arrival"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTextAndTheCriteriaAreConditionsOfTheQuery() {
        Root<BookingEntity> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        BookingDBQueryService.matching(" García ", new BookingCriteria("MRU01", Set.of(BookingStatus.Confirmed),
                        LocalDate.of(2026, 10, 1), null, null, LocalDate.of(2026, 10, 9)))
                .toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb, times(3)).like(any(), eq("%garcía%"));
        verify(cb).equal(root.get("hotelCode"), "MRU01");
        verify(root.get("status")).in(List.of("Confirmed"));
        verify(cb).greaterThanOrEqualTo(root.<LocalDate>get("arrival"), LocalDate.of(2026, 10, 1));
        verify(cb).lessThanOrEqualTo(root.<LocalDate>get("departure"), LocalDate.of(2026, 10, 9));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nothingAskedIsNoCondition() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        BookingDBQueryService.matching(null, null).toPredicate(mock(Root.class, RETURNS_DEEP_STUBS), mock(CriteriaQuery.class), cb);
        verify(cb, never()).like(any(), any(String.class));
        verify(cb, never()).equal(any(), any(Object.class));
    }
}
