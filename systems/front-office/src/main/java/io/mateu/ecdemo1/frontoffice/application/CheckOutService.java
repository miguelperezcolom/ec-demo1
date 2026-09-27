package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The check-out of an in-house stay: the stay leaves and its room is freed, together. */
@Service
public class CheckOutService {

  final StayRepository stays;
  final RoomRepository rooms;

  public CheckOutService(StayRepository stays, RoomRepository rooms) {
    this.stays = stays;
    this.rooms = rooms;
  }

  /** Checks the stay out and frees its room (to be cleaned). A stay not in house is left as it is. */
  @Transactional
  public Stay checkOut(String stayId) {
    var stay = stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
    if (!stay.inHouse()) {
      return stay;
    }
    var departed = stays.save(stay.completeCheckOut());
    rooms.findByNumber(stay.roomNumber()).map(Room::release).ifPresent(rooms::save);
    return departed;
  }
}
