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

  static String freeRoom(RoomRepository rooms) {
    var room = String.valueOf(9900 + SEQ.incrementAndGet());
    rooms.save(new Room(room, 99, "Suite test", RoomOccupancy.FREE, HousekeepingStatus.CLEAN, null));
    return room;
  }

  private Fixtures() {}
}
