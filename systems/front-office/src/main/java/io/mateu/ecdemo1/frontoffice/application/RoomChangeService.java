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

  public RoomChangeService(StayRepository stays, RoomRepository rooms) {
    this.stays = stays;
    this.rooms = rooms;
  }

  /** The room the stay moved to; empty if that room does not exist or is not free. */
  @Transactional
  public Optional<Room> changeRoom(String stayId, String roomNumber) {
    var stay = stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
    var room = rooms.findByNumber(roomNumber).filter(Room::assignable).orElse(null);
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
