package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A reservation written onto its stay — whoever tells it: the PMS through the pms-fo integration, or
 * a tool through {@code /api/reservations}. Idempotent — writing the same reservation twice leaves one
 * stay — and respectful of the desk: what the reservation says replaces what the stay had, never what
 * happened at the hotel.
 */
@Service
public class StayWrites {

  /** The guest the reservation names, as the reservation has them. */
  public record Holder(String name, String document, String email, String phone) {}

  /**
   * @param guestId    the cardex's id of the holder: the chain's customer code when there is one
   * @param roomType   and {@code board} in words the desk reads
   * @param companions the room's other people; empty is "the reservation does not say" when told so
   */
  public record Booking(String guestId, Holder holder, List<Companion> companions, String roomType, String board,
      LocalDate checkIn, LocalDate checkOut, int pax, String agency, BigDecimal total) {}

  public record Result(String stayId, String guestId, String status, boolean created) {}

  final StayRepository stays;
  final GuestRepository guests;

  public StayWrites(StayRepository stays, GuestRepository guests) {
    this.stays = stays;
    this.guests = guests;
  }

  /**
   * Writes the booking onto stay {@code stayId}, created if it is not there.
   *
   * @param keepCompanionsWhenNoneSaid a booking with no companions leaves the stay's as they are — the
   *                                   PMS does not usually keep them, and not saying is not "nobody"
   */
  @Transactional
  public Result write(String stayId, Booking b, boolean keepCompanionsWhenNoneSaid) {
    var existing = stays.findById(stayId);
    var holder = b.holder();
    var guestId = b.guestId();
    var guest = guests.findById(guestId)
        .map(g -> g.withReservationData(holder.name(), holder.document(), holder.email(), holder.phone()))
        .orElseGet(() -> Guest.fromReservation(guestId, holder.name(), holder.document(), holder.email(), holder.phone()));
    // A walk-in's guest was the desk's until the chain named the customer: what the desk took down
    // at the counter — the document it saw, the contact — goes on with the chain's customer.
    var deskGuest = existing.map(Stay::guestId).filter(g -> g.startsWith("wi-") && !g.equals(guestId))
        .flatMap(guests::findById);
    if (deskGuest.isPresent()) {
      guest = guest.withDeskData(deskGuest.get());
    }
    // The chain now names another customer for the stay's guest — two customers found to be one, as when
    // the desk scanned a document the chain already knew: the identity the desk saw goes on with it.
    var previousGuest = existing.map(Stay::guestId).filter(g -> !g.startsWith("wi-") && !g.equals(guestId))
        .flatMap(guests::findById).filter(Guest::identityComplete);
    if (previousGuest.isPresent() && !guest.identityComplete()) {
      var seen = previousGuest.get().document();
      if (guest.document() == null || guest.document().isBlank() || guest.document().equals(seen)) {
        guest = guest.verifyIdentity(seen);
      }
    }
    guests.save(guest);
    var companions = b.companions() == null ? List.<Companion>of() : b.companions();
    var stay = existing
        .map(s -> s.applyReservation(guestId, b.roomType(), b.board(), b.checkIn(), b.checkOut(), b.pax(), b.agency(),
            b.total(), keepCompanionsWhenNoneSaid && companions.isEmpty() ? s.companions() : companions))
        .orElseGet(() -> Stay.fromReservation(stayId, guestId, b.roomType(), b.board(), b.checkIn(), b.checkOut(),
            b.pax(), b.agency(), b.total(), companions));
    stays.save(stay);
    return new Result(stayId, guestId, stay.status().name(), existing.isEmpty());
  }
}
