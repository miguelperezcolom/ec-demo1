package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChanges;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged.NoticeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The reception notices at the desk, through the application layer on the real adapters (H2): a
 * blocking check-in notice of the holder or of a companion who is a chain customer stops the check-in
 * until it is read; a kárdex change Salesforce rejected or has not decided stops the check-out until
 * «Entendido». Refusals and acknowledgements are audited.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guest-notices;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class GuestNoticesTest {

  static final AtomicInteger SEQ = new AtomicInteger();

  @Autowired GuestNotices notices;
  @Autowired CheckInService checkIn;
  @Autowired CheckOutService checkOut;
  @Autowired GuestRepository guests;
  @Autowired StayRepository stays;
  @Autowired RoomRepository rooms;
  @Autowired KardexChanges kardex;
  @Autowired JdbcTemplate jdbc;

  @Test
  void aBlockingCheckInNoticeStopsTheCheckInUntilTheDeskReadsIt() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    notices.take(notice(a.guestId(), NoticeType.BLOCKING, List.of(NoticeMoment.CHECK_IN), true, 1));
    var stay = stays.findById(a.stayId()).orElseThrow();

    assertThat(notices.blockingAtCheckIn(stay)).singleElement()
        .satisfies(p -> assertThat(p.pax()).isEqualTo(1));
    assertThatThrownBy(() -> checkIn.checkIn(a.stayId(), null, List.of(), "ana"))
        .isInstanceOf(GuestNotices.NotAcknowledged.class).hasMessageContaining("He leído el aviso");
    assertThat(stays.findById(a.stayId()).orElseThrow().status()).isEqualTo(StayStatus.ARRIVING);
    assertThat(audited(a.stayId())).singleElement().asString()
        .contains("Check-in refused").contains("\"succeeded\":false").contains("\"by\":\"ana\"");

    notices.acknowledgeCheckIn(a.stayId(), "ana", null);
    assertThat(checkIn.checkIn(a.stayId(), null, List.of(), "ana").status()).isEqualTo(StayStatus.IN_HOUSE);
    assertThat(audited(a.stayId())).hasSize(2).last().asString()
        .contains("Read check-in notices").contains("\"succeeded\":true").contains("He leído el aviso");
  }

  @Test
  void aCompanionWhoIsAChainCustomerHasTheirNoticesToo() {
    var n = SEQ.incrementAndGet();
    var a = Fixtures.arrival(guests, stays, rooms, 2);
    var companion = "C-COMP" + n;
    var stay = stays.findById(a.stayId()).orElseThrow();
    stays.save(Stay.fromReservation(stay.id(), stay.guestId(), stay.roomType(), stay.board(), stay.checkIn(),
        stay.checkOut(), 2, stay.agency(), stay.total(),
        List.of(new Companion(companion, "Luis Acompañante", null, false, null, null, "Adulto")))
        .assignRoom(stay.roomNumber(), stay.roomType()));
    notices.take(notice(companion, NoticeType.BLOCKING, List.of(NoticeMoment.CHECK_IN), true, 1));

    var blocking = notices.blockingAtCheckIn(stays.findById(a.stayId()).orElseThrow());

    assertThat(blocking).singleElement().satisfies(p -> {
      assertThat(p.pax()).isEqualTo(2);
      assertThat(p.guestName()).isEqualTo("Luis Acompañante");
    });
    assertThatThrownBy(() -> checkIn.checkIn(a.stayId(), null, List.of()))
        .isInstanceOf(GuestNotices.NotAcknowledged.class);
  }

  @Test
  void whatIsNotBlockingNotForTheCheckInInactiveOrOutOfTheStayDoesNotStopIt() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    notices.take(notice(a.guestId(), NoticeType.IMPORTANT, List.of(NoticeMoment.CHECK_IN), true, 1));
    notices.take(notice(a.guestId(), NoticeType.BLOCKING, List.of(NoticeMoment.CHECK_OUT), true, 1));
    notices.take(notice(a.guestId(), NoticeType.BLOCKING, List.of(NoticeMoment.CHECK_IN), false, 1));
    var future = notice(a.guestId(), NoticeType.BLOCKING, List.of(NoticeMoment.CHECK_IN), true, 1);
    notices.take(new CustomerNoticeChanged(future.eventId(), future.occurredAt(), future.noticeId(), 1, a.guestId(),
        "Desde el mes que viene", NoticeType.BLOCKING, LocalDate.now().plusDays(30), null,
        List.of(NoticeMoment.CHECK_IN), true, null));

    var stay = stays.findById(a.stayId()).orElseThrow();
    assertThat(notices.forStay(stay, io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNotice.Moment.CHECK_IN))
        .singleElement().satisfies(p -> assertThat(p.notice().typeLabel()).isEqualTo("Importante"));
    assertThat(checkIn.checkIn(a.stayId(), null, List.of()).status()).isEqualTo(StayStatus.IN_HOUSE);
  }

  @Test
  void aNewBlockingNoticeAfterTheReadingIsNotRead() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    notices.take(notice(a.guestId(), NoticeType.BLOCKING, List.of(NoticeMoment.CHECK_IN), true, 1));
    var prepared = notices.checkInFingerprint(stays.findById(a.stayId()).orElseThrow());
    notices.acknowledgeCheckIn(a.stayId(), "ana", prepared);

    notices.take(notice(a.guestId(), NoticeType.BLOCKING, List.of(NoticeMoment.CHECK_IN), true, 1));

    assertThat(notices.checkInAcknowledged(stays.findById(a.stayId()).orElseThrow())).isFalse();
    // As prepared before the new one, the reading no longer covers what there is.
    assertThatThrownBy(() -> notices.acknowledgeCheckIn(a.stayId(), "agent", prepared))
        .isInstanceOf(GuestNotices.NotAcknowledged.class).hasMessageContaining("han cambiado");
    assertThatThrownBy(() -> checkIn.checkIn(a.stayId(), null, List.of()))
        .isInstanceOf(GuestNotices.NotAcknowledged.class);
  }

  @Test
  void theMdmsVersionsKeepTheNewestAndAnEventIsTakenOnce() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    var id = "AV-" + SEQ.incrementAndGet();
    var v2 = new CustomerNoticeChanged("E-" + id + "-2", Instant.now(), id, 2, a.guestId(), "v2", NoticeType.BLOCKING,
        null, null, List.of(NoticeMoment.CHECK_IN), false, null);
    var v1 = new CustomerNoticeChanged("E-" + id + "-1", Instant.now(), id, 1, a.guestId(), "v1", NoticeType.BLOCKING,
        null, null, List.of(NoticeMoment.CHECK_IN), true, null);

    assertThat(notices.take(v2)).isTrue();
    assertThat(notices.take(v2)).as("the same event again").isFalse();
    assertThat(notices.take(v1)).as("an older version").isFalse();

    // v2 deactivated it: nothing blocks.
    assertThat(notices.blockingAtCheckIn(stays.findById(a.stayId()).orElseThrow())).isEmpty();
  }

  @Test
  void aKardexChangeSalesforceRejectedStopsTheCheckOutUntilEntendido() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    checkIn.checkIn(a.stayId(), null, List.of());
    var now = Instant.now();
    kardex.save(new KardexChange(a.guestId(), "CR-FO-1", KardexChange.KardexStatus.REJECTED, "email",
        List.of(new KardexChange.FieldChange("email", "ana@old.example", "ana@new.example")), "No es su email",
        now, now, true));
    notices.take(notice(a.guestId(), NoticeType.IMPORTANT, List.of(NoticeMoment.CHECK_OUT), true, 1));
    var stay = stays.findById(a.stayId()).orElseThrow();

    var warnings = notices.checkOutWarnings(stay);
    assertThat(warnings.kardex()).singleElement().satisfies(k -> {
      assertThat(k.rejected()).isTrue();
      assertThat(k.lines()).anySatisfy(l -> assertThat(l).contains("Email").contains("ana@new.example")
          .contains("rechazado por Salesforce"));
      assertThat(k.lines()).contains("Motivo: No es su email");
    });
    assertThat(warnings.notices()).hasSize(1);

    assertThatThrownBy(() -> checkOut.checkOut(a.stayId(), "ana"))
        .isInstanceOf(GuestNotices.NotAcknowledged.class).hasMessageContaining("Entendido");
    assertThat(stays.findById(a.stayId()).orElseThrow().status()).isEqualTo(StayStatus.IN_HOUSE);

    notices.acknowledgeCheckOut(a.stayId(), "ana", null);
    assertThat(checkOut.checkOut(a.stayId(), "ana").status()).isEqualTo(StayStatus.DEPARTED);
    assertThat(audited(a.stayId())).anySatisfy(p -> assertThat(p).contains("Check-out refused"))
        .anySatisfy(p -> assertThat(p).contains("Read check-out warnings").contains("Entendido"));
  }

  @Test
  void aKardexChangeStillPendingWarnsThatTheInvoiceKeepsTheOldData() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    checkIn.checkIn(a.stayId(), null, List.of());
    kardex.save(KardexChange.pending(a.guestId(),
        List.of(new KardexChange.FieldChange("nombre", "Ana Test", "Ana Nueva")), Instant.now()).sent("CR-FO-2"));

    var warnings = notices.checkOutWarnings(stays.findById(a.stayId()).orElseThrow());

    assertThat(warnings.kardex()).singleElement().satisfies(k -> {
      assertThat(k.rejected()).isFalse();
      assertThat(k.lines()).singleElement().asString().contains("la factura saldrá con el dato anterior");
    });
    assertThatThrownBy(() -> checkOut.checkOut(a.stayId())).isInstanceOf(GuestNotices.NotAcknowledged.class);
  }

  @Test
  void aStayWithNothingToWarnOfLeavesAsBefore() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    checkIn.checkIn(a.stayId(), null, List.of());

    assertThat(notices.checkOutWarnings(stays.findById(a.stayId()).orElseThrow()).any()).isFalse();
    assertThat(checkOut.checkOut(a.stayId()).status()).isEqualTo(StayStatus.DEPARTED);
  }

  static CustomerNoticeChanged notice(String customerId, NoticeType type, List<NoticeMoment> showAt, boolean active,
                                      long version) {
    var id = "AV-" + SEQ.incrementAndGet();
    return new CustomerNoticeChanged(UUID.randomUUID().toString(), Instant.now(), id, version, customerId,
        "Aviso " + id, type, LocalDate.now().minusDays(1), null, showAt, active, "500" + id);
  }

  List<String> audited(String stayId) {
    var needle = "\\\"stayId\\\":\\\"" + stayId + "\\\"";
    return jdbc.queryForList("select payload from outbox_message where binding = 'audit' order by seq", String.class)
        .stream().filter(p -> p.contains(needle)).toList();
  }
}
