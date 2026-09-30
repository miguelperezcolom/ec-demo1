package io.mateu.ecdemo1.notices.application;

import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeType;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.SubjectType;
import io.mateu.ecdemo1.notices.InMemory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A reservation's and a partner's notices are kept here and published whole on every change; the
 * customers' are Salesforce's — taken from the MDM, published again, never edited here. What the desk
 * sees is filtered by subject, hotel, moment and the days of the stay.
 */
class NoticesTest {

    final InMemory memory = new InMemory();
    final Notices notices = memory.notices;

    static Notices.Draft reservation(String locator, String hotel, NoticeType type, Set<NoticeMoment> moments,
                                     LocalDate from, LocalDate to) {
        return new Notices.Draft(SubjectType.RESERVATION, locator, hotel, "Cuna en la habitación", type, from, to,
                moments, true);
    }

    @Test
    void aReservationNoticeIsKeptAndPublishedWholeKeyedByItsSubject() {
        var n = notices.create(reservation("12e45", "mru01", NoticeType.IMPORTANT,
                EnumSet.of(NoticeMoment.PRE_ARRIVAL, NoticeMoment.CHECK_IN), null, null), "Ana");

        assertThat(n.subjectId).isEqualTo("12E45");
        assertThat(n.hotelCode).isEqualTo("MRU01");
        assertThat(memory.published).singleElement().satisfies(e -> {
            assertThat(e.key()).isEqualTo("RESERVATION:12E45");
            assertThat(e.version()).isEqualTo(1);
            assertThat(e.moments()).containsExactly(NoticeMoment.PRE_ARRIVAL, NoticeMoment.CHECK_IN);
            assertThat(e.source()).isEqualTo("NOTICES");
            assertThat(e.active()).isTrue();
        });

        notices.setActive(n.id, false, "Ana");
        assertThat(memory.published).last().satisfies(e -> {
            assertThat(e.version()).isEqualTo(2);
            assertThat(e.active()).isFalse();
        });
    }

    @Test
    void aPartnerNoticeCarriesThePartnersNameAndAnUnknownPartnerIsRefused() {
        var n = notices.create(new Notices.Draft(SubjectType.PARTNER, "nordtravel", null, "Bono obligatorio",
                NoticeType.BLOCKING, null, null, EnumSet.of(NoticeMoment.CHECK_IN), true), "Ana");
        assertThat(n.subjectName).isEqualTo("Nordic Travel Group AB");
        assertThat(memory.published.getLast().subjectName()).isEqualTo("Nordic Travel Group AB");

        assertThatThrownBy(() -> notices.create(new Notices.Draft(SubjectType.PARTNER, "NADIE", null, "x",
                NoticeType.INFORMATIVE, null, null, EnumSet.of(NoticeMoment.CHECK_IN), true), "Ana"))
                .hasMessageContaining("NADIE");
    }

    @Test
    void aCustomersNoticeIsNotCreatedNorChangedHere() {
        assertThatThrownBy(() -> notices.create(new Notices.Draft(SubjectType.CUSTOMER, "C-1", null, "x",
                NoticeType.INFORMATIVE, null, null, EnumSet.of(NoticeMoment.CHECK_IN), true), "Ana"))
                .hasMessageContaining("Salesforce");

        notices.take(customer("E-1", 2, List.of(CustomerNoticeChanged.NoticeMoment.CHECK_IN), true));
        assertThatThrownBy(() -> notices.update("AV-7F3A2C", reservation("C-00042", null, NoticeType.INFORMATIVE,
                EnumSet.of(NoticeMoment.CHECK_IN), null, null), "Ana")).hasMessageContaining("Salesforce");
        assertThatThrownBy(() -> notices.setActive("AV-7F3A2C", false, "Ana")).hasMessageContaining("Salesforce");
    }

    @Test
    void aNoticeWithoutTextOrMomentsOrWithDatesTheWrongWayRoundIsRefused() {
        assertThatThrownBy(() -> notices.create(new Notices.Draft(SubjectType.RESERVATION, "12E45", null, " ",
                NoticeType.INFORMATIVE, null, null, EnumSet.of(NoticeMoment.CHECK_IN), true), "Ana"))
                .hasMessageContaining("texto");
        assertThatThrownBy(() -> notices.create(reservation("12E45", null, NoticeType.INFORMATIVE, Set.of(), null,
                null), "Ana")).hasMessageContaining("momento");
        assertThatThrownBy(() -> notices.create(reservation("12E45", null, NoticeType.INFORMATIVE,
                EnumSet.of(NoticeMoment.CHECK_IN), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)), "Ana"))
                .hasMessageContaining("anterior");
        assertThat(memory.published).isEmpty();
    }

    static CustomerNoticeChanged customer(String eventId, long version, List<CustomerNoticeChanged.NoticeMoment> showAt,
                                          boolean active) {
        return new CustomerNoticeChanged(eventId, Instant.parse("2026-11-12T09:00:00Z"), "AV-7F3A2C", version,
                "C-00042", "Cliente alérgico a los frutos secos", CustomerNoticeChanged.NoticeType.IMPORTANT, null, null,
                showAt, active, "500d1000009XyZBAA0");
    }

    @Test
    void aCustomersNoticeFromTheMdmIsPublishedAgainWithStayAsInHouseOnceAndNeverOlder() {
        assertThat(notices.take(customer("E-1", 2, List.of(CustomerNoticeChanged.NoticeMoment.CHECK_IN,
                CustomerNoticeChanged.NoticeMoment.STAY), true))).isTrue();
        var event = memory.published.getLast();
        assertThat(event.subjectType()).isEqualTo(SubjectType.CUSTOMER);
        assertThat(event.subjectId()).isEqualTo("C-00042");
        assertThat(event.moments()).containsExactly(NoticeMoment.CHECK_IN, NoticeMoment.IN_HOUSE);
        assertThat(event.version()).isEqualTo(2);
        assertThat(event.source()).isEqualTo("SALESFORCE");
        assertThat(event.sourceRef()).isEqualTo("500d1000009XyZBAA0");

        // the same event again (redelivered), and an older version, change nothing
        assertThat(notices.take(customer("E-1", 2, List.of(CustomerNoticeChanged.NoticeMoment.CHECK_IN), true))).isFalse();
        assertThat(notices.take(customer("E-0", 1, List.of(CustomerNoticeChanged.NoticeMoment.CHECK_IN), false))).isFalse();
        // a newer one does
        assertThat(notices.take(customer("E-3", 3, List.of(CustomerNoticeChanged.NoticeMoment.CHECK_OUT), false))).isTrue();
        assertThat(memory.published).hasSize(2);
        assertThat(memory.published.getLast().active()).isFalse();
        assertThat(memory.published.getLast().moments()).containsExactly(NoticeMoment.CHECK_OUT);
    }

    @Test
    void whatTheDeskSeesIsFilteredBySubjectHotelMomentAndTheDaysOfTheStay() {
        var arrival = LocalDate.of(2026, 11, 20);
        var departure = LocalDate.of(2026, 11, 24);
        var chain = notices.create(reservation("12E45", null, NoticeType.INFORMATIVE,
                EnumSet.of(NoticeMoment.CHECK_IN), null, null), "Ana");
        var here = notices.create(reservation("12E45", "MRU01", NoticeType.BLOCKING,
                EnumSet.of(NoticeMoment.CHECK_IN), null, null), "Ana");
        notices.create(reservation("12E45", "PMI01", NoticeType.IMPORTANT, EnumSet.of(NoticeMoment.CHECK_IN), null,
                null), "Ana");
        notices.create(reservation("12E45", null, NoticeType.IMPORTANT, EnumSet.of(NoticeMoment.CHECK_OUT), null,
                null), "Ana");
        notices.create(reservation("12E45", null, NoticeType.IMPORTANT, EnumSet.of(NoticeMoment.CHECK_IN),
                LocalDate.of(2026, 11, 25), null), "Ana");
        var overlapping = notices.create(reservation("12E45", null, NoticeType.IMPORTANT,
                EnumSet.of(NoticeMoment.CHECK_IN), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20)), "Ana");
        var inactive = notices.create(reservation("12E45", null, NoticeType.IMPORTANT,
                EnumSet.of(NoticeMoment.CHECK_IN), null, null), "Ana");
        notices.setActive(inactive.id, false, "Ana");
        notices.create(reservation("99X99", null, NoticeType.IMPORTANT, EnumSet.of(NoticeMoment.CHECK_IN), null,
                null), "Ana");

        var seen = notices.applicable(SubjectType.RESERVATION, "12e45", "MRU01", NoticeMoment.CHECK_IN, arrival,
                departure);

        assertThat(seen).extracting(n -> n.id).containsExactly(here.id, overlapping.id, chain.id);
        assertThat(notices.applicable(SubjectType.PARTNER, "12E45", "MRU01", NoticeMoment.CHECK_IN, arrival,
                departure)).isEmpty();
    }

    @Test
    void everyNoticeCanBePublishedAgainAsItIs() {
        notices.create(reservation("12E45", null, NoticeType.INFORMATIVE, EnumSet.of(NoticeMoment.CHECK_IN), null,
                null), "Ana");
        notices.take(customer("E-1", 4, List.of(CustomerNoticeChanged.NoticeMoment.CHECK_IN), true));
        memory.published.clear();

        assertThat(notices.republishAll()).isEqualTo(2);
        assertThat(memory.published).extracting(NoticeChanged::version).containsExactlyInAnyOrder(1L, 4L);
    }
}
