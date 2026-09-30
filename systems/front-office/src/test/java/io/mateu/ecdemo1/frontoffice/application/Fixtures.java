package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.room.HousekeepingStatus;
import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomOccupancy;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** A guest, a free room and an arriving stay of their own for each test. */
final class Fixtures {

  static final AtomicInteger SEQ = new AtomicInteger();

  record Arrival(String stayId, String guestId, String room) {}

  static Arrival arrival(GuestRepository guests, StayRepository stays, RoomRepository rooms, int pax) {
    var n = SEQ.incrementAndGet();
    var guestId = "C-APP" + n;
    var room = String.valueOf(9900 + n);
    guests.save(Guest.fromReservation(guestId, "Ana Test " + n, "X" + n, "ana" + n + "@example.com", null));
    rooms.save(new Room(room, 99, "Doble test", RoomOccupancy.FREE, HousekeepingStatus.INSPECTED, null));
    var stayId = "APP-" + n;
    stays.save(Stay.fromReservation(stayId, guestId, "Doble", "Desayuno", LocalDate.now(), LocalDate.now().plusDays(3),
        pax, "Directo · WEB", new BigDecimal("300.00"), List.of()).assignRoom(room, "Doble test"));
    return new Arrival(stayId, guestId, room);
  }

  /**
   * Everything the check-in needs, done: the holder's and every companion's document seen and the
   * registration signed — a check-in without them is refused unless it is forced. The stay's id.
   */
  static String complete(String stayId, GuestRepository guests, StayRepository stays,
                         io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository ops) {
    var stay = stays.findById(stayId).orElseThrow();
    guests.findById(stay.guestId()).filter(g -> !g.identityComplete())
        .ifPresent(g -> guests.save(g.verifyIdentity(g.document() == null ? "DOC-" + g.id() : g.document())));
    for (int pax = 2; pax <= stay.pax(); pax++) {
      var companion = stay.companionAt(pax);
      if (companion == null || !companion.identityComplete()) {
        stay = stay.scanCompanion(pax, "DOC-" + stayId + "-" + pax);
      }
    }
    stays.save(stay);
    ops.save(stayId, ops.of(stayId).withFirma(true));
    return stayId;
  }

  static String freeRoom(RoomRepository rooms) {
    var room = String.valueOf(9900 + SEQ.incrementAndGet());
    rooms.save(new Room(room, 99, "Suite test", RoomOccupancy.FREE, HousekeepingStatus.CLEAN, null));
    return room;
  }

  private Fixtures() {}
}
