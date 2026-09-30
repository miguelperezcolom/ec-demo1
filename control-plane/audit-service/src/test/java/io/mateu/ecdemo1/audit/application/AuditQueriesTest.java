package io.mateu.ecdemo1.audit.application;

import io.mateu.ecdemo1.audit.store.AuditRecord;
import io.mateu.ecdemo1.audit.store.AuditRecordRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** The trail's query service: what reaches the database of the page asked for and of the search. */
class AuditQueriesTest {

    final AuditRecordRepository records = mock(AuditRecordRepository.class);
    final AuditQueries queries = new AuditQueries(records, "UTC");

    @SuppressWarnings("unchecked")
    Pageable pageAsked(AuditQuery query, Pageable pageable) {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(records.findAll(any(Specification.class), captor.capture())).thenReturn(Page.empty());
        queries.find(query, pageable);
        return captor.getValue();
    }

    @Test
    void thePageAskedForIsThePageReadNewestFirstWhenItNamesNoOrder() {
        var asked = pageAsked(AuditQuery.everything(), PageRequest.of(3, 50));
        assertThat(asked.getPageNumber()).isEqualTo(3);
        assertThat(asked.getPageSize()).isEqualTo(50);
        assertThat(asked.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "at"));
    }

    @Test
    void anOrderAskedForIsKept() {
        var asked = pageAsked(AuditQuery.everything(), PageRequest.of(1, 20, Sort.by("actor")));
        assertThat(asked.getSort()).isEqualTo(Sort.by("actor"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theRecordsOfThePageComeBackAsTheyAre() {
        var record = new AuditRecord();
        record.actionId = "a1";
        when(records.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record), PageRequest.of(0, 20), 41));
        var page = queries.find(null, PageRequest.of(0, 20));
        assertThat(page.getContent()).containsExactly(record);
        assertThat(page.getTotalElements()).isEqualTo(41);
    }

    @SuppressWarnings("unchecked")
    static CriteriaBuilder where(AuditQuery query) {
        Root<AuditRecord> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> criteria = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        AuditQueries.matching(query, ZoneId.of("UTC")).toPredicate(root, criteria, cb);
        return cb;
    }

    @Test
    void theFreeTextLooksInEveryColumnLowerCased() {
        var cb = where(new AuditQuery("  XDBL ", null, null, null, null, null, null, null));
        verify(cb, times(AuditQueries.SEARCHED.size())).like(any(), eq("%xdbl%"));
    }

    @Test
    void eachFilterIsAConditionOfItsOwn() {
        var cb = where(new AuditQuery(null, "MRU01", "luis", "Pause", "integrations",
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22), false));
        verify(cb).like(any(), eq("%mru01%"));
        verify(cb).like(any(), eq("%luis%"));
        verify(cb).like(any(), eq("%pause%"));
        verify(cb).like(any(), eq("%integrations%"));
        // the days are whole days in the trail's zone: from the first's start to the day after the last
        verify(cb).greaterThanOrEqualTo(any(), eq(Instant.parse("2026-09-21T00:00:00Z")));
        verify(cb).lessThan(any(), eq(Instant.parse("2026-09-23T00:00:00Z")));
        verify(cb).equal(any(), eq(false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aReservationsHistoryIsNewestFirstAndAskingForNoneReadsNothing() {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(records.findAll(any(Specification.class), captor.capture())).thenReturn(Page.empty());

        assertThat(queries.ofReservation(null, " ", 50)).isEmpty();
        verify(records, never()).findAll(any(Specification.class), any(Pageable.class));

        queries.ofReservation("FO-1", "97R5DW", 5000);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "at"));
        assertThat(captor.getValue().getPageSize()).isEqualTo(500);
    }

    @Test
    void nothingAskedIsNoCondition() {
        var cb = where(new AuditQuery(" ", "", null, null, null, null, null, null));
        verify(cb, never()).like(any(), any(String.class));
        verify(cb, never()).equal(any(), any(Object.class));
    }
}
