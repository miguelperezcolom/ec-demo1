package io.mateu.ecdemo1.integrations.application;

import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integrations.store.BackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.Integration;
import io.mateu.ecdemo1.integrations.store.IntegrationRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The integrations' query service: the page and the search the database is asked for. */
class IntegrationQueriesTest {

    final IntegrationRepository integrations = mock(IntegrationRepository.class);
    final IntegrationQueries queries = new IntegrationQueries(integrations, mock(BackfillRunRepository.class));

    @SuppressWarnings("unchecked")
    Pageable pageAsked(Pageable pageable) {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(integrations.findAll(any(Specification.class), captor.capture())).thenReturn(Page.empty());
        queries.find("", pageable);
        return captor.getValue();
    }

    @Test
    void theyAreReadAPageAtATimeByHotelUnlessAnotherOrderIsAsked() {
        var asked = pageAsked(PageRequest.of(1, 20));
        assertThat(asked.getPageNumber()).isEqualTo(1);
        assertThat(asked.getPageSize()).isEqualTo(20);
        assertThat(asked.getSort()).isEqualTo(Sort.by("crsHotelCode"));
        assertThat(pageAsked(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "status"))).getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTextLooksInHotelPropertyNameAndTheStatusesItNames() {
        Root<Integration> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        IntegrationQueries.matching(" Paused").toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb, times(3)).like(any(), eq("%paused%"));
        verify(root.get("status")).in(List.of(IntegrationStatus.PAUSED));
    }

    @Test
    @SuppressWarnings("unchecked")
    void noTextIsEveryIntegration() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        IntegrationQueries.matching(null).toPredicate(mock(Root.class), mock(CriteriaQuery.class), cb);
        verify(cb).conjunction();
        verify(cb, never()).like(any(), any(String.class));
    }

    @Test
    void aTextThatNamesNoStatusMatchesNone() {
        assertThat(IntegrationQueries.statusesNamedBy("mru01")).isEmpty();
        assertThat(IntegrationQueries.statusesNamedBy("pending"))
                .containsExactlyInAnyOrder(IntegrationStatus.PENDING_CONFIGURATION, IntegrationStatus.MAPPING_PENDING);
    }
}
