package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A stay moves to another room: assigned before the arrival, or a move of a guest already in the
 * house — who then leaves the old room and takes the new one, in the same transaction.
 */
@Service
public class RoomChangeService {

  final StayRepository stays;
  final RoomRepository rooms;
  final io.mateu.ecdemo1.frontoffice.infra.pms.PmsRooms pmsRooms;
  final io.mateu.ecdemo1.frontoffice.infra.pms.PmsLinks links;
  final io.mateu.ecdemo1.frontoffice.infra.pms.ReceptionReports reception;

  public RoomChangeService(StayRepository stays, RoomRepository rooms,
                           io.mateu.ecdemo1.frontoffice.infra.pms.PmsRooms pmsRooms,
                           io.mateu.ecdemo1.frontoffice.infra.pms.PmsLinks links,
                           io.mateu.ecdemo1.frontoffice.infra.pms.ReceptionReports reception) {
    this.stays = stays;
    this.rooms = rooms;
    this.pmsRooms = pmsRooms;
    this.links = links;
    this.reception = reception;
  }

  /** Whether the PMS refused this stay's check-in: the guests are in at the desk, not in Opera yet. */
  boolean checkInRefused(String stayId) {
    return links.stateOf(stayId).map(s -> s.startsWith("Opera: rechazado (check-in)")).orElse(false);
  }

  /** The room the stay moved to; empty if that room does not exist or is not free. */
  @Transactional
  public Optional<Room> changeRoom(String stayId, String roomNumber) {
    var stay = stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
    var room = rooms.findByNumber(roomNumber).filter(Room::assignable).orElse(null);
    var refused = stay.inHouse() && checkInRefused(stayId);
    if (room == null && (!stay.inHouse() || refused)) {
      // A room of the PMS (the master of the stay): assigned to the stay here, and in Opera at the
      // check-in (registrar-checkin), which refuses it if Opera does.
      var pms = pmsRooms.find(roomNumber);
      pms.ifPresent(r -> {
        var moved = stays.save(stay.assignRoom(r.number(), r.type() != null ? r.type() : stay.roomType()));
        if (refused) {
          // Opera refused the check-in (the room, say): it goes up again, with this room.
          reception.checkedIn(moved, "front office (another room)");
        }
      });
      return pms;
    }
    if (room == null) {
      return Optional.empty();
    }
    if (stay.inHouse()) {
      rooms.findByNumber(stay.roomNumber()).map(Room::release).ifPresent(rooms::save);
      rooms.save(room.occupy());
    }
    stays.save(stay.assignRoom(room.number(), room.typeLabel()));
    return Optional.of(room);
  }
}
