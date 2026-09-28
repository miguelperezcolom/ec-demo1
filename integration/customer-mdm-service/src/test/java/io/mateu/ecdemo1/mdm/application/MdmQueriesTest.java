package io.mateu.ecdemo1.mdm.application;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import io.mateu.ecdemo1.mdm.store.Consolidation;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.Source;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import io.mateu.ecdemo1.mdm.store.XrefRepository;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The MDM's query services: the page and the search the database is asked for. */
class MdmQueriesTest {

    final CustomerRepository customerRepository = mock(CustomerRepository.class);
    final SourceRepository sources = mock(SourceRepository.class);
    final CustomerQueries customers = new CustomerQueries(customerRepository, sources, mock(XrefRepository.class),
            mock(ChangeRequestRepository.class));
    final ChangeRequestRepository changeRequestRepository = mock(ChangeRequestRepository.class);
    final ChangeRequestQueries changeRequests = new ChangeRequestQueries(changeRequestRepository);
    final ConsolidationRepository consolidationRepository = mock(ConsolidationRepository.class);
    final ConsolidationQueries consolidations = new ConsolidationQueries(consolidationRepository);

    @SuppressWarnings("unchecked")
    Pageable customersPageAsked(Pageable pageable) {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(customerRepository.findAll(any(Specification.class), captor.capture())).thenReturn(Page.empty());
        customers.search(CustomerSearch.text("ana"), pageable);
        return captor.getValue();
    }

    @Test
    void customersAreReadAPageAtATimeMostRecentlyChangedFirstUnlessAnotherOrderIsAsked() {
        var asked = customersPageAsked(PageRequest.of(2, 20));
        assertThat(asked.getPageNumber()).isEqualTo(2);
        assertThat(asked.getPageSize()).isEqualTo(20);
        assertThat(asked.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "updatedAt"));
        assertThat(customersPageAsked(PageRequest.of(0, 20, Sort.by("email"))).getSort()).isEqualTo(Sort.by("email"));
    }

    @SuppressWarnings("unchecked")
    static CriteriaBuilder where(CustomerSearch search, Root<Customer> root) {
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        CustomerQueries.matching(search).toPredicate(root, mock(CriteriaQuery.class), cb);
        return cb;
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyWordOfTheTextIsLookedForInEveryColumnAndMergedCustomersAreLeftOut() {
        Root<Customer> root = mock(Root.class, RETURNS_DEEP_STUBS);
        var cb = where(CustomerSearch.text(" Ana  600 "), root);
        verify(cb).isNull(root.get("aliasOf"));
        verify(cb, times(CustomerQueries.SEARCHED.size())).like(any(), eq("%ana%"));
        // a word of three digits or more is also looked for in the phone's digits
        verify(cb, times(CustomerQueries.SEARCHED.size() + 1)).like(any(), eq("%600%"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theFiltersAreConditionsOfTheirOwnNormalisedAsTheyAreCompared() {
        Root<Customer> root = mock(Root.class, RETURNS_DEEP_STUBS);
        var cb = where(new CustomerSearch(null, "Ana García", " ANA@X.COM", "+34 600-11", "12345678-z",
                Set.of(CustomerStatus.CONSOLIDATED)), root);
        verify(cb).like(any(), eq("%ana%"));
        verify(cb).like(any(), eq("%garcía%"));
        verify(cb).like(any(), eq("%ana@x.com%"));
        verify(cb).like(any(), eq("%3460011%"));
        verify(cb).like(any(), eq("%12345678z%"));
        verify(root.get("status")).in(Set.of(CustomerStatus.CONSOLIDATED));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPhoneWithNoDigitsIsComparedAsText() {
        var cb = where(new CustomerSearch(null, null, null, "casa", null, null), mock(Root.class, RETURNS_DEEP_STUBS));
        verify(cb).like(any(), eq("%casa%"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void goldenRecordsLookForTheWholeTextInCodeNameEmailAndDocument() {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(customerRepository.findAll(any(Specification.class), captor.capture())).thenReturn(Page.empty());
        customers.goldenRecords("x", PageRequest.of(1, 20));
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "updatedAt"));

        Root<Customer> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        CustomerQueries.containing(" Ana García ").toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb, times(4)).like(any(), eq("%ana garcía%"));
        verify(cb, atLeastOnce()).isNull(root.get("aliasOf"));
    }

    @Test
    void aReservationIsCountedOnceWhateverItsPassengers() {
        when(sources.findByCustomerIdOrderByFirstSeenAsc("C1")).thenReturn(List.of(source("MRU01", "L1"),
                source("MRU01", "L1"), source("MRU01", "L2")));
        assertThat(customers.reservationsOf("C1")).isEqualTo(2);
    }

    @Test
    void aCustomerIsLabelledByNameAndCode() {
        var c = new Customer();
        c.id = "C1";
        c.firstName = "Ana";
        c.lastName = "García";
        when(customerRepository.findById("C1")).thenReturn(Optional.of(c));
        assertThat(customers.label("C1")).isEqualTo("Ana García · C1");
        assertThat(customers.label("C9")).isEqualTo("C9");
    }

    static Source source(String hotel, String locator) {
        var s = new Source();
        s.hotelCode = hotel;
        s.locator = locator;
        return s;
    }

    @Test
    @SuppressWarnings("unchecked")
    void changeRequestsAreReadAPageAtATimeNewestFirstNarrowedByStatus() {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(changeRequestRepository.findAll(any(Specification.class), captor.capture())).thenReturn(Page.empty());
        changeRequests.find(null, Set.of(ChangeRequest.Status.PENDING), PageRequest.of(3, 20));
        assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "requestedAt"));

        Root<ChangeRequest> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        ChangeRequestQueries.matching(null, Set.of(ChangeRequest.Status.PENDING))
                .toPredicate(root, mock(CriteriaQuery.class, RETURNS_DEEP_STUBS), cb);
        verify(root.get("status")).in(List.of("PENDING"));
        verify(cb, never()).like(any(), any(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTextOfAChangeRequestIsLookedForInItsColumnsAndItsCustomersName() {
        Root<ChangeRequest> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        ChangeRequestQueries.matching(" Email ", null).toPredicate(root, mock(CriteriaQuery.class, RETURNS_DEEP_STUBS), cb);
        // its own columns, and the full name of the customer in the subquery
        verify(cb, times(ChangeRequestQueries.SEARCHED.size() + 1)).like(any(), eq("%email%"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void consolidationsAreReadAPageAtATimeNewestFirst() {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(consolidationRepository.findAll(any(Specification.class), captor.capture())).thenReturn(Page.empty());
        consolidations.page(ConsolidationQueries.Search.all(), PageRequest.of(1, 20));
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "receivedAt"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void consolidationsAreSearchedInBothCodesAndTheDetailAndKeptToTheViaAndPropagationAskedFor() {
        Root<Consolidation> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        ConsolidationQueries.matching(new ConsolidationQueries.Search(" C01 ", Set.of("EVENT"),
                        Set.of(ConsolidationQueries.Propagation.REMOVED, ConsolidationQueries.Propagation.PENDING)))
                .toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb, times(3)).like(any(), eq("%c01%"));
        verify(root.get("via")).in(Set.of("EVENT"));
        // REMOVED: no survivor; PENDING: a survivor, not propagated yet
        verify(cb, atLeastOnce()).isNull(root.get("survivorId"));
        verify(cb, atLeastOnce()).isNull(root.get("propagatedAt"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void goldenRecordsAreKeptToTheSalesforceStatesAndStatusesAskedFor() {
        var captor = ArgumentCaptor.forClass(Specification.class);
        when(customerRepository.findAll(captor.capture(), any(Pageable.class))).thenReturn(Page.empty());
        customers.goldenRecords(null, Set.of(SalesforceState.FAILED), Set.of(CustomerStatus.PROVISIONAL), PageRequest.of(0, 20));

        Root<Customer> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(root.get("salesforceState")).in(Set.of(SalesforceState.FAILED));
        verify(root.get("status")).in(Set.of(CustomerStatus.PROVISIONAL));
    }
}
