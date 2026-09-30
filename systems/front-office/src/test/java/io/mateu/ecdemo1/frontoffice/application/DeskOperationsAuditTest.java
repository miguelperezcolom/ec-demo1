package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.infra.audit.AuditOutbox;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The desk's operations audit themselves, whoever asks: who, what, on which stay — once each, a failure
 * too, and a refusal only where it was decided. Through the real application services (H2).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:desk-audit;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@Import(DeskOperationsAuditTest.Capture.class)
class DeskOperationsAuditTest {

  static final List<AuditedAction> AUDITED = new CopyOnWriteArrayList<>();

  /** Imported, not scanned: another test's context must keep the real audit outbox. */
  static class Capture {
    @Bean
    @Primary
    AuditOutbox capturingAudit() {
      return new AuditOutbox(null) {
        @Override
        protected void write(AuditedAction action) {
          AUDITED.add(action);
        }
      };
    }
  }

  @Autowired CheckInService checkIn;
  @Autowired RoomChangeService roomChange;
  @Autowired FolioService folios;
  @Autowired KardexService kardex;
  @Autowired GuestRepository guests;
  @Autowired StayRepository stays;
  @Autowired RoomRepository rooms;
  @Autowired CheckInOpsRepository ops;

  @BeforeEach
  void clear() {
    AUDITED.clear();
  }

  List<AuditedAction> of(String stayId) {
    return AUDITED.stream().filter(a -> a.parameters() != null && a.parameters().contains("\"" + stayId + "\"")).toList();
  }

  @Test
  void aCheckInIsOneRecordNamingWhoAndTheRoom() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    Fixtures.complete(a.stayId(), guests, stays, ops);

    checkIn.checkIn(a.stayId(), null, List.of(), "ana");

    assertThat(of(a.stayId())).singleElement().satisfies(r -> {
      assertThat(r.action()).isEqualTo("Check-in");
      assertThat(r.by()).isEqualTo("ana");
      assertThat(r.succeeded()).isTrue();
      assertThat(r.response()).contains("En casa");
      assertThat(r.parameters()).contains("\"locator\":\"" + a.stayId() + "\"");
    });
  }

  @Test
  void aRefusedCheckInIsAuditedOnceWhereItWasRefused() {
    var a = Fixtures.arrival(guests, stays, rooms, 1); // nothing done: a document and the signature missing

    assertThatThrownBy(() -> checkIn.checkIn(a.stayId(), null, List.of(), "ana"))
        .isInstanceOf(IncompleteCheckIns.CheckInIncomplete.class);

    assertThat(of(a.stayId())).singleElement().satisfies(r -> {
      assertThat(r.action()).isEqualTo("Check-in refused: steps missing");
      assertThat(r.by()).isEqualTo("ana");
      assertThat(r.succeeded()).isFalse();
    });
  }

  @Test
  void aRoomChangeSaysFromAndToAndARoomThatIsNotThereIsAFailure() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    var free = Fixtures.freeRoom(rooms);

    StayAudit.as("luis", () -> roomChange.changeRoom(a.stayId(), free));
    StayAudit.as("luis", () -> roomChange.changeRoom(a.stayId(), "NO-SUCH-ROOM"));

    assertThat(of(a.stayId())).satisfiesExactly(
        r -> {
          assertThat(r.action()).isEqualTo("Room change");
          assertThat(r.by()).isEqualTo("luis");
          assertThat(r.succeeded()).isTrue();
          assertThat(r.parameters()).contains("\"from\":\"" + a.room() + "\"", "\"to\":\"" + free + "\"");
        },
        r -> {
          assertThat(r.succeeded()).isFalse();
          assertThat(r.response()).contains("NO-SUCH-ROOM");
        });
  }

  @Test
  void aChargeTheCatalogDoesNotHaveIsAFailureAndNothingIsCharged() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);

    assertThat(folios.postCharge(a.stayId(), "NO-SUCH-CHARGE", "ana")).isEmpty();

    assertThat(of(a.stayId())).singleElement().satisfies(r -> {
      assertThat(r.action()).isEqualTo("Charge posted");
      assertThat(r.succeeded()).isFalse();
    });
  }

  @Test
  void aKardexEditSaysWhichFieldsNotTheirValues() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);

    StayAudit.as("ana", () -> {
      kardex.registered(a.stayId(), 1, "Y1234567Z", "Ana Nueva", "ana.nueva@example.com", null);
      return null;
    });

    assertThat(of(a.stayId())).singleElement().satisfies(r -> {
      assertThat(r.action()).isEqualTo("Kardex edit");
      assertThat(r.by()).isEqualTo("ana");
      assertThat(r.parameters()).contains("documento", "nombre", "email").doesNotContain("Y1234567Z", "ana.nueva");
    });
  }
}
