package io.mateu.ecdemo1.frontoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChanges;
import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomOccupancy;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Each use case is one transaction: a failure halfway — the last write of a check-in, of a check-out,
 * of a room change, of a kárdex edit — leaves nothing of it saved.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:use-case-tx;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class UseCaseTransactionalityTest {

  @MockitoSpyBean FolioRepository folios;
  @MockitoSpyBean RoomRepository rooms;
  @MockitoSpyBean KardexChanges kardexChanges;

  @Autowired CheckInService checkIn;
  @Autowired CheckOutService checkOut;
  @Autowired RoomChangeService roomChange;
  @Autowired KardexService kardex;
  @Autowired GuestRepository guests;
  @Autowired StayRepository stays;
  @Autowired CheckInOpsRepository ops;

  @Test
  void aCheckInThatFailsOpeningTheFolioLeavesTheStayTheRoomAndTheOperationsAsTheyWere() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    doThrow(new IllegalStateException("the folio could not be written")).when(folios).save(any(Folio.class));

    assertThatThrownBy(() -> checkIn.checkIn(a.stayId(), null, List.of("transfer")))
        .hasMessage("the folio could not be written");

    var stay = stays.findById(a.stayId()).orElseThrow();
    assertThat(stay.status()).isEqualTo(StayStatus.ARRIVING);
    assertThat(stay.addOns()).isEmpty();
    assertThat(rooms.findByNumber(a.room()).orElseThrow().occupancy()).isEqualTo(RoomOccupancy.FREE);
    assertThat(folios.findByStayId(a.stayId())).isEmpty();
    assertThat(ops.of(a.stayId()).extras()).isFalse();
  }

  @Test
  void aCheckOutThatFailsFreeingTheRoomLeavesTheGuestInTheHouse() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    checkIn.checkIn(a.stayId(), null, List.of());
    doThrow(new IllegalStateException("the room could not be written")).when(rooms).save(any(Room.class));

    assertThatThrownBy(() -> checkOut.checkOut(a.stayId())).hasMessage("the room could not be written");

    assertThat(stays.findById(a.stayId()).orElseThrow().status()).isEqualTo(StayStatus.IN_HOUSE);
  }

  @Test
  void aRoomChangeThatFailsOccupyingTheNewRoomLeavesTheGuestAndTheOldRoomAsTheyWere() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    checkIn.checkIn(a.stayId(), null, List.of());
    var other = Fixtures.freeRoom(rooms);
    // the old room is freed first; taking the new one fails
    doThrow(new IllegalStateException("the new room could not be written"))
        .when(rooms).save(org.mockito.ArgumentMatchers.argThat((Room r) -> r != null && r.number().equals(other)));

    assertThatThrownBy(() -> roomChange.changeRoom(a.stayId(), other)).hasMessage("the new room could not be written");

    assertThat(stays.findById(a.stayId()).orElseThrow().roomNumber()).isEqualTo(a.room());
    assertThat(rooms.findByNumber(a.room()).orElseThrow().occupancy()).isEqualTo(RoomOccupancy.OCCUPIED);
    assertThat(rooms.findByNumber(other).orElseThrow().occupancy()).isEqualTo(RoomOccupancy.FREE);
  }

  @Test
  void aKardexEditWhoseChangeCannotBeKeptForTheMasterDoesNotChangeTheGuest() {
    var a = Fixtures.arrival(guests, stays, rooms, 1);
    doThrow(new IllegalStateException("the change could not be kept")).when(kardexChanges).save(any(KardexChange.class));

    assertThatThrownBy(() -> kardex.registered(a.stayId(), 1, "Y123", "Otra Persona", "otra@example.com", null))
        .hasMessage("the change could not be kept");

    var guest = guests.findById(a.guestId()).orElseThrow();
    assertThat(guest.name()).startsWith("Ana Test");
    assertThat(guest.identityComplete()).isFalse();
  }
}
