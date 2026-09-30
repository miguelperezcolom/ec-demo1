package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.ForcedCheckIns;
import io.mateu.ecdemo1.frontoffice.domain.stay.PendingStep;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.infra.audit.AuditOutbox;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.messaging.OutboxMessage;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * «Forzar check-in»: a check-in with steps missing (a companion's document, the signature) is refused
 * unless forced with a reason; forced, the guest is in (and the check-in goes up to the PMS) but the stay
 * is «Check-in incompleto» — audited — and cannot check out until the steps are done. Past the
 * documents' deadline (24 h) with a document still missing, reception is told once.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:forced-check-in;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class ForcedCheckInTest {

  @Autowired CheckInService checkIn;
  @Autowired CheckOutService checkOut;
  @Autowired KardexService kardex;
  @Autowired IncompleteCheckIns incomplete;
  @Autowired StayQueries queries;
  @Autowired GuestRepository guests;
  @Autowired StayRepository stays;
  @Autowired RoomRepository rooms;
  @Autowired CheckInOpsRepository ops;
  @Autowired ForcedCheckIns forcedCheckIns;
  @Autowired AuditOutbox audit;
  @Autowired Outbox outbox;
  @Autowired CommandOutbox commands;
  @Autowired PlatformTransactionManager transactions;

  /** Two pax; the holder's document seen and the registration signed — the companion's document is missing. */
  String arrivalMissingTheCompanionsDocument() {
    var a = Fixtures.arrival(guests, stays, rooms, 2);
    Fixtures.complete(a.stayId(), guests, stays, ops);
    var stay = stays.findById(a.stayId()).orElseThrow();
    stays.save(stay.registerCompanion(2, io.mateu.ecdemo1.frontoffice.domain.stay.Companion.pending(2)));
    return a.stayId();
  }

  @Test
  void aCheckInWithAStepMissingIsRefusedSayingWhatAndAudited() {
    var stayId = arrivalMissingTheCompanionsDocument();

    assertThat(incomplete.missing(stays.findById(stayId).orElseThrow())).extracting(PendingStep::label)
        .containsExactly("Documento de Huésped 2 (pax 2)");
    assertThatThrownBy(() -> checkIn.checkIn(stayId, null, List.of(), "ana"))
        .isInstanceOf(IncompleteCheckIns.CheckInIncomplete.class)
        .hasMessageContaining("Documento de Huésped 2").hasMessageContaining("fuerza el check-in");

    assertThat(stays.findById(stayId).orElseThrow().status()).isEqualTo(StayStatus.ARRIVING);
    assertThat(audited(stayId)).anySatisfy(p -> assertThat(p).contains("Check-in refused: steps missing")
        .contains("\"succeeded\":false"));
  }

  @Test
  void forcingNeedsAReason() {
    var stayId = arrivalMissingTheCompanionsDocument();

    assertThatThrownBy(() -> checkIn.forceCheckIn(stayId, null, List.of(), "  ", "ana"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
    assertThat(stays.findById(stayId).orElseThrow().status()).isEqualTo(StayStatus.ARRIVING);
  }

  @Test
  void aForcedCheckInLetsTheGuestInGoesUpToThePmsAndIsAudited() {
    var stayId = arrivalMissingTheCompanionsDocument();

    var stay = checkIn.forceCheckIn(stayId, null, List.of(), "El acompañante trae el pasaporte mañana", "ana");

    assertThat(stay.status()).isEqualTo(StayStatus.IN_HOUSE);
    // up to the PMS like any check-in
    assertThat(commands.all(CommandOutbox.FRONT_OFFICE_EVENTS)).filteredOn(e -> e.key().equals("MRU01/" + stayId))
        .extracting(OutboxMessage::type).containsExactly("GuestCheckedIn");
    var forced = forcedCheckIns.of(stayId).orElseThrow();
    assertThat(forced.forcedBy()).isEqualTo("ana");
    assertThat(forced.reason()).isEqualTo("El acompañante trae el pasaporte mañana");
    assertThat(forced.missingWhenForced()).isEqualTo("Documento de Huésped 2 (pax 2)");
    assertThat(forced.forcedAt()).isNotNull();
    var status = incomplete.status(stayId);
    assertThat(status.incomplete()).isTrue();
    assertThat(status.overdue()).isFalse();
    assertThat(status.documentsDue()).isEqualTo(forced.forcedAt().plus(Duration.ofHours(24)));
    assertThat(audited(stayId)).anySatisfy(p -> assertThat(p).contains("\"action\":\"Forced check-in\"")
        .contains("\"by\":\"ana\"").contains("pasaporte mañana").contains("\"succeeded\":true"));
  }

  @Test
  void theCheckOutIsRefusedUntilTheStepsAreDoneAndThenItGoes() {
    var stayId = arrivalMissingTheCompanionsDocument();
    checkIn.forceCheckIn(stayId, null, List.of(), "Pasaporte en la maleta", "ana");

    assertThatThrownBy(() -> checkOut.checkOut(stayId, "luis"))
        .isInstanceOf(IncompleteCheckIns.CheckInIncomplete.class).hasMessageContaining("Check-in incompleto")
        .hasMessageContaining("Documento de Huésped 2");
    assertThat(stays.findById(stayId).orElseThrow().status()).isEqualTo(StayStatus.IN_HOUSE);
    assertThat(audited(stayId)).anySatisfy(p -> assertThat(p).contains("Check-out refused: check-in incomplete")
        .contains("\"by\":\"luis\""));
    assertThatThrownBy(() -> incomplete.complete(stayId, "luis"))
        .isInstanceOf(IncompleteCheckIns.CheckInIncomplete.class);

    // «Completar»: the desk scans the companion's document — the last step owed
    kardex.scanned(stayId, 2);

    assertThat(incomplete.status(stayId).incomplete()).isFalse();
    assertThat(forcedCheckIns.of(stayId).orElseThrow().completedAt()).isNotNull();
    assertThat(audited(stayId)).anySatisfy(p -> assertThat(p).contains("Forced check-in completed"));
    assertThat(outbox.messages("notification-resolutions")).anySatisfy(m ->
        assertThat(m.key()).isEqualTo("front-office/forced-check-in/" + stayId));
    assertThat(checkOut.checkOut(stayId, "luis").status()).isEqualTo(StayStatus.DEPARTED);
  }

  @Test
  void aMissingSignatureAloneCanBeCompletedFromTheWizard() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    Fixtures.complete(a.stayId(), guests, stays, ops);
    ops.save(a.stayId(), ops.of(a.stayId()).withFirma(false));
    checkIn.forceCheckIn(a.stayId(), null, List.of(), "La tablet no funciona", "ana");
    assertThat(incomplete.status(a.stayId()).missing()).extracting(PendingStep::kind)
        .containsExactly(PendingStep.Kind.SIGNATURE);

    checkIn.registrationSigned(a.stayId(), "ana");

    assertThat(incomplete.complete(a.stayId(), "ana").incomplete()).isFalse();
  }

  @Test
  void pastTheDeadlineWithADocumentMissingReceptionIsToldOnce() {
    var stayId = arrivalMissingTheCompanionsDocument();
    checkIn.forceCheckIn(stayId, null, List.of(), "Sin el DNI del menor", "ana");
    var forcedAt = forcedCheckIns.of(stayId).orElseThrow().forcedAt();

    assertThat(incomplete.notifyOverdue(forcedAt.plus(Duration.ofHours(23)))).isZero();
    assertThat(notices(stayId)).isEmpty();

    assertThat(incomplete.notifyOverdue(forcedAt.plus(Duration.ofHours(24)).plusSeconds(1))).isEqualTo(1);
    // idempotent: the next checks do not tell again
    assertThat(incomplete.notifyOverdue(forcedAt.plus(Duration.ofHours(25)))).isZero();

    assertThat(notices(stayId)).singleElement().satisfies(m -> {
      Contracts.topic("notifications").assertValid(m.payload());
      assertThat(m.payload()).contains("\"type\":\"CHECK_IN_INCOMPLETE\"").contains("\"hotelCode\":\"MRU01\"")
          .contains("Documento de Huésped 2").contains("Sin el DNI del menor")
          .contains("\"subject\":\"front-office/forced-check-in/" + stayId + "\"");
    });
    assertThat(forcedCheckIns.of(stayId).orElseThrow().overdueNotifiedAt()).isNotNull();

    // the notice on the stay turns red: seen with a clock past the deadline
    var later = new IncompleteCheckIns(stays, guests, ops, forcedCheckIns, audit, outbox, transactions, "MRU01",
        Duration.ofHours(24), "", Clock.offset(Clock.systemUTC(), Duration.ofHours(25)));
    assertThat(later.status(stayId).overdue()).isTrue();
    assertThat(incomplete.status(stayId).overdue()).isFalse();
  }

  @Test
  void onlyTheSignatureMissingIsNotATravellersRegistrationMatter() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    Fixtures.complete(a.stayId(), guests, stays, ops);
    ops.save(a.stayId(), ops.of(a.stayId()).withFirma(false));
    checkIn.forceCheckIn(a.stayId(), null, List.of(), "La tablet no funciona", "ana");
    var forcedAt = forcedCheckIns.of(a.stayId()).orElseThrow().forcedAt();

    assertThat(incomplete.notifyOverdue(forcedAt.plus(Duration.ofHours(30)))).isZero();
    assertThat(notices(a.stayId())).isEmpty();
  }

  @Test
  void aCompleteCheckInIsNotForcedEvenIfAskedTo() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    Fixtures.complete(a.stayId(), guests, stays, ops);

    checkIn.forceCheckIn(a.stayId(), null, List.of(), "por si acaso", "ana");

    assertThat(forcedCheckIns.of(a.stayId())).isEmpty();
    assertThat(incomplete.status(a.stayId()).incomplete()).isFalse();
  }

  List<OutboxMessage> notices(String stayId) {
    return outbox.messages("notifications").stream().filter(m -> m.payload().contains(stayId)).toList();
  }

  List<String> audited(String stayId) {
    return outbox.messages("audit").stream().map(OutboxMessage::payload)
        .filter(p -> p.contains("\\\"stayId\\\":\\\"" + stayId + "\\\"")).toList();
  }
}
