package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.infra.pms.ReceptionReports;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The check-out of an in-house stay: the stay leaves and its room is freed, together. */
@Service
public class CheckOutService {

  final StayRepository stays;
  final RoomRepository rooms;
  final GuestNotices notices;
  final ReceptionReports reception;
  final IncompleteCheckIns incomplete;
  final StayAudit audit;

  public CheckOutService(StayRepository stays, RoomRepository rooms, GuestNotices notices, ReceptionReports reception,
                         IncompleteCheckIns incomplete, StayAudit audit) {
    this.stays = stays;
    this.rooms = rooms;
    this.notices = notices;
    this.reception = reception;
    this.incomplete = incomplete;
    this.audit = audit;
  }

  /**
   * Checks the stay out and frees its room (to be cleaned). A stay not in house is left as it is.
   * Refused ({@link GuestNotices.NotAcknowledged}) while its warnings — a kárdex change Salesforce
   * rejected or has not decided, a check-out notice — are not acknowledged («Entendido»); and
   * ({@link IncompleteCheckIns.CheckInIncomplete}) while its check-in, forced, is still incomplete.
   */
  @Transactional
  public Stay checkOut(String stayId) {
    return checkOut(stayId, null);
  }

  /** As {@link #checkOut(String)}, saying who asks — audited, done or refused. */
  @Transactional
  public Stay checkOut(String stayId, String by) {
    return audit.run("Check-out", stayId, by, null, () -> checkOutNow(stayId, by),
        s -> s.inHouse() ? "Estancia " + s.status() : "Salida registrada · habitación " + s.roomNumber() + " liberada");
  }

  Stay checkOutNow(String stayId, String by) {
    var stay = stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("Reserva " + stayId + " no encontrada"));
    if (!stay.inHouse()) {
      return stay;
    }
    incomplete.requireComplete(stay, by);
    notices.requireCheckOut(stay, by);
    var departed = stays.save(stay.completeCheckOut());
    rooms.findByNumber(stay.roomNumber()).map(Room::release).ifPresent(rooms::save);
    // The PMS is the master of the stay and of its folio: the check-out goes up to it, with this
    // transaction; its invoice comes back to the stay.
    reception.checkedOut(departed, by);
    // and the stay is closed for the chain: its customer history learns of it, in this transaction too
    reception.closed(departed, by);
    return departed;
  }
}
