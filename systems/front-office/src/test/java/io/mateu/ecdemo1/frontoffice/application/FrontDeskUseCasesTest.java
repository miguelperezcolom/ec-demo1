package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioLine;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.room.HousekeepingStatus;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomOccupancy;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.crs.WalkInDesk;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The desk's use cases, each through its application service, against the real adapters (H2). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:use-cases;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class FrontDeskUseCasesTest {

  @Autowired CheckInService checkIn;
  @Autowired CheckOutService checkOut;
  @Autowired RoomChangeService roomChange;
  @Autowired FolioService folioService;
  @Autowired NoShowService noShows;
  @Autowired KardexService kardex;
  @Autowired WalkInService walkIns;
  @Autowired StayQueries queries;
  @Autowired GuestRepository guests;
  @Autowired StayRepository stays;
  @Autowired RoomRepository rooms;
  @Autowired FolioRepository folios;
  @Autowired CheckInOpsRepository ops;
  @Autowired WalkIns walkInStore;
  @Autowired CommandOutbox outbox;

  @Test
  void aCheckInMovesTheStayInOccupiesTheRoomOpensTheFolioAndClosesTheExtras() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);

    var stay = checkIn.checkIn(a.stayId(), null, List.of("transfer"));

    assertThat(stay.status()).isEqualTo(StayStatus.IN_HOUSE);
    assertThat(stays.findById(a.stayId()).orElseThrow().status()).isEqualTo(StayStatus.IN_HOUSE);
    assertThat(rooms.findByNumber(a.room()).orElseThrow().occupancy()).isEqualTo(RoomOccupancy.OCCUPIED);
    var folio = folios.findByStayId(a.stayId()).orElseThrow();
    assertThat(folio.preauthorized()).isEqualByComparingTo("300.00");
    assertThat(folio.lines()).extracting(FolioLine::concept)
        .containsExactly("Alojamiento x3 noches", "Transfer aeropuerto");
    assertThat(folio.balance()).isEqualByComparingTo("345.00");
    assertThat(ops.of(a.stayId()).extras()).isTrue();
  }

  @Test
  void theDeskChecksInDirectlyOnlyWhenNothingIsMissing() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    var stay = stays.findById(a.stayId()).orElseThrow();
    assertThat(queries.readyForDirectCheckIn(stay)).isFalse(); // identity not seen, extras open

    kardex.scanned(a.stayId(), 1);
    assertThat(queries.pendingPax(stay)).isZero();
    checkIn.extrasClosed(a.stayId());

    assertThat(queries.readyForDirectCheckIn(stay)).isTrue();
  }

  @Test
  void theChosenExtrasAndTheClosedSelectionAreSavedTogether() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);

    var stay = checkIn.extrasChosen(a.stayId(), Map.of("transfer", true, "parking", false));

    assertThat(stay.addOns()).extracting(x -> x.addOnId()).containsExactly("transfer");
    assertThat(ops.of(a.stayId()).extras()).isTrue();
  }

  @Test
  void aCheckOutFreesTheRoomToBeCleaned() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    checkIn.checkIn(a.stayId(), null, List.of());

    var departed = checkOut.checkOut(a.stayId());

    assertThat(departed.status()).isEqualTo(StayStatus.DEPARTED);
    var room = rooms.findByNumber(a.room()).orElseThrow();
    assertThat(room.occupancy()).isEqualTo(RoomOccupancy.FREE);
    assertThat(room.housekeeping()).isEqualTo(HousekeepingStatus.DIRTY);
  }

  @Test
  void aGuestInTheHouseWhoChangesRoomLeavesTheOldOneAndTakesTheNew() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    checkIn.checkIn(a.stayId(), null, List.of());
    var other = Fixtures.freeRoom(rooms);

    var moved = roomChange.changeRoom(a.stayId(), other);

    assertThat(moved).isPresent();
    assertThat(stays.findById(a.stayId()).orElseThrow().roomNumber()).isEqualTo(other);
    assertThat(stays.findById(a.stayId()).orElseThrow().roomType()).isEqualTo("Suite test");
    assertThat(rooms.findByNumber(other).orElseThrow().occupancy()).isEqualTo(RoomOccupancy.OCCUPIED);
    assertThat(rooms.findByNumber(a.room()).orElseThrow().occupancy()).isEqualTo(RoomOccupancy.FREE);
    // an occupied room is no room to move to
    assertThat(roomChange.changeRoom(a.stayId(), a.room())).isPresent();
    assertThat(roomChange.changeRoom(Fixtures.arrival(guests, stays, rooms, 1).stayId(), a.room())).isEmpty();
  }

  @Test
  void theLateCheckOutIsChargedOnceAtItsFee() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);

    assertThat(folioService.contractLateCheckOut(a.stayId())).isTrue();
    assertThat(folioService.contractLateCheckOut(a.stayId())).isFalse();

    var folio = folios.findByStayId(a.stayId()).orElseThrow();
    assertThat(folio.id()).isEqualTo(Folio.idFor(a.stayId()));
    assertThat(folio.lines()).extracting(FolioLine::concept).containsExactly(Folio.LATE_CHECK_OUT);
    assertThat(folio.balance()).isEqualByComparingTo(Folio.LATE_CHECK_OUT_FEE);
    assertThat(folio.lateCheckOutContracted()).isTrue();
  }

  @Test
  void whenNobodyOfTheReservationArrivesTheCrsIsToldAndAMarkCanBeTakenBack() {
    var a = Fixtures.arrival(guests, stays, rooms, 2);

    var first = noShows.paxToggled(a.stayId(), 1);
    assertThat(first.noShow()).isTrue();
    assertThat(first.nobodyArrived()).isFalse();
    assertThat(first.crsNotice()).isNull();

    var all = noShows.paxToggled(a.stayId(), 2);
    assertThat(all.nobodyArrived()).isTrue();
    assertThat(all.crsNotice()).contains("Se comunica al CRS");
    // The report is a command for the CRS adapter, in the outbox with the mark.
    assertThat(outbox.all(CommandOutbox.NO_SHOW_REPORTS)).filteredOn(e -> e.key().equals("MRU01/" + a.stayId()))
        .singleElement().satisfies(e -> assertThat(e.payload())
            .contains("\"hotelCode\":\"MRU01\"", "\"locator\":\"" + a.stayId() + "\"", "\"commandId\":\"NS-"));

    var back = noShows.paxToggled(a.stayId(), 1);
    assertThat(back.noShow()).isFalse();
    assertThat(ops.of(a.stayId()).noShowPax()).containsExactly(2);
  }

  @Test
  void aScanOfTheHolderAndOfACompanionSendsTheirDocumentsToTheMdm() {
    var a = Fixtures.arrival(guests, stays, rooms, 2);

    var holder = kardex.scanned(a.stayId(), 1);
    var companion = kardex.scanned(a.stayId(), 2);

    // The holder's document is the one the stay has; the companion's, made up — and seen.
    assertThat(holder.documentNumber()).isEqualTo(guests.findById(a.guestId()).orElseThrow().document());
    assertThat(guests.findById(a.guestId()).orElseThrow().identityComplete()).isTrue();
    var registered = stays.findById(a.stayId()).orElseThrow().companionAt(2);
    assertThat(registered.identityComplete()).isTrue();
    assertThat(registered.document()).isEqualTo(companion.documentNumber());
    assertThat(companion.birthDate()).isNotNull();
    assertThat(companion.nationality()).isNotBlank();

    // Both to the MDM as commands: the holder by its customer, the companion by the reservation and its pax.
    var commands = outbox.all(CommandOutbox.CUSTOMER_COMMANDS).stream()
        .filter(e -> e.payload().contains("\"stayId\":\"" + a.stayId() + "\"")).toList();
    assertThat(commands).hasSize(2);
    assertThat(commands.get(0).key()).isEqualTo(a.guestId());
    assertThat(commands.get(0).payload()).contains("\"type\":\"record-scanned-identity\"", "\"pax\":1",
        "\"customerId\":\"" + a.guestId() + "\"", "\"documentNumber\":\"" + holder.documentNumber() + "\"");
    assertThat(commands.get(1).key()).isEqualTo("MRU01/" + a.stayId());
    assertThat(commands.get(1).payload()).contains("\"pax\":2", "\"customerId\":null", "\"locator\":\"" + a.stayId() + "\"",
        "\"documentNumber\":\"" + companion.documentNumber() + "\"", "\"birthDate\":\"" + companion.birthDate() + "\"");

    // Scanned again: the same document.
    assertThat(kardex.scanned(a.stayId(), 2)).isEqualTo(companion);
  }

  @Test
  void aKardexEditOfTheHolderKeepsTheGuestAndThePendingChangeForTheMaster() {
    var a = Fixtures.arrival(guests, stays, rooms, 2);

    kardex.registered(a.stayId(), 1, "", "Ana María Test", "ana.maria@example.com", "+34 600");
    kardex.registered(a.stayId(), 2, "", "Luis Test", null, null);

    var guest = guests.findById(a.guestId()).orElseThrow();
    assertThat(guest.name()).isEqualTo("Ana María Test");
    assertThat(guest.identityComplete()).isTrue();
    assertThat(kardex.changeOf(a.guestId())).get().extracting(KardexChange::status)
        .isEqualTo(KardexChange.KardexStatus.PENDING);
    var companion = stays.findById(a.stayId()).orElseThrow().companionAt(2);
    assertThat(companion.name()).isEqualTo("Luis Test");
    assertThat(companion.document()).isEqualTo("MAN-" + a.stayId().toUpperCase() + "-P2");
  }

  @Test
  void aWalkInWithoutTheHoldersDocumentOpensNothing() {
    var request = new WalkInDesk.Request(null, null, LocalDate.now(), LocalDate.now().plusDays(1), "STD", "BAR", "BB",
        2, List.of(), new WalkInDesk.Holder("Nora", "Vega", null, null, "ES", "PASSPORT", " "), null);

    assertThatThrownBy(() -> walkIns.confirm(request, new BigDecimal("100.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Falta del titular: documento.");
    assertThat(walkInStore.pending()).isEmpty();
  }
}
