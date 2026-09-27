package io.mateu.ecdemo1.frontoffice.infra.api;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the integration writes the reservations the CRS makes, as it writes them to the PMS: each
 * becomes a stay to arrive, its holder a guest of the cardex. Idempotent — writing the same
 * reservation twice leaves one stay — and respectful of the desk: a change from the CRS replaces
 * what the reservation says, never what happened at the hotel. A walk-in the desk opened comes back
 * as the reservation the CRS made of it, and is written onto the stay it already is.
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationsApi {

  /** A person of the reservation: the holder, or a companion in the room. */
  public record Person(String customerId, String name, String document, String email, String phone) {}

  /**
   * @param holder the guest: its {@code customerId} — the chain's MDM code — is the cardex's id
   * @param roomType, board as the PMS names them, in words the desk reads
   * @param agency who sold it, if not the hotel itself
   */
  /**
   * @param externalReference the channel's own reference: for a walk-in this front office made, its
   *                          stay's id — the stay the reservation is, already here
   * @param pmsReservationId  where Opera has it, when Opera was written first
   */
  public record Reservation(Person holder, List<Person> companions, String roomType, String board,
      LocalDate checkIn, LocalDate checkOut, int pax, String agency, BigDecimal total, String externalReference,
      String pmsReservationId) {

    public Reservation(Person holder, List<Person> companions, String roomType, String board, LocalDate checkIn,
        LocalDate checkOut, int pax, String agency, BigDecimal total) {
      this(holder, companions, roomType, board, checkIn, checkOut, pax, agency, total, null, null);
    }
  }

  public record Written(String stayId, String guestId, String status, boolean created) {}

  final StayRepository stays;
  final GuestRepository guests;
  final WalkIns walkIns;

  public ReservationsApi(StayRepository stays, GuestRepository guests, WalkIns walkIns) {
    this.stays = stays;
    this.guests = guests;
    this.walkIns = walkIns;
  }

  /**
   * The stay a CRS locator is: its own, or — for a walk-in this desk opened — the one the booking was
   * made from, found by the reference it carries (the stay's id) or by the locator the CRS gave it.
   */
  String stayOf(String locator, String externalReference) {
    if (stays.findById(locator).isPresent()) {
      return locator;
    }
    return walkIns.byLocator(locator).map(WalkIn::stayId)
        .or(() -> externalReference == null ? java.util.Optional.empty()
            : walkIns.of(externalReference).map(WalkIn::stayId))
        .orElse(locator);
  }

  @PutMapping("/{locator}")
  @Transactional
  public Written write(@PathVariable String locator, @RequestBody Reservation r) {
    var stayId = stayOf(locator, r.externalReference());
    var existing = stays.findById(stayId);
    var holder = r.holder();
    var guestId = holder.customerId() == null || holder.customerId().isBlank() ? "crs-" + locator : holder.customerId();
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
    var companions = companions(r);
    var stay = existing
        .map(s -> s.applyReservation(guestId, r.roomType(), r.board(), r.checkIn(), r.checkOut(), r.pax(), r.agency(),
            r.total(), companions))
        .orElseGet(() -> Stay.fromReservation(locator, guestId, r.roomType(), r.board(), r.checkIn(), r.checkOut(),
            r.pax(), r.agency(), r.total(), companions));
    stays.save(stay);
    walkIns.of(stayId).ifPresent(w -> walkIns.save(w.cameBack(locator, r.pmsReservationId(), java.time.Instant.now())));
    return new Written(stayId, guestId, stay.status().name(), existing.isEmpty());
  }

  /** Why, and what it still costs: a no-show is a stay the guest owes its fee for. */
  public record Cancellation(Boolean noShow, String reasonCode, BigDecimal total) {}

  @PostMapping("/{locator}/cancellation")
  @Transactional
  public Written cancel(@PathVariable String locator,
      @org.springframework.web.bind.annotation.RequestBody(required = false) Cancellation cancellation) {
    var stay = stays.findById(stayOf(locator, null)).orElseThrow(() -> new NoSuchElementException("No stay " + locator));
    var noShow = cancellation != null && Boolean.TRUE.equals(cancellation.noShow());
    var cancelled = stays.save(noShow ? stay.noShow(cancellation.total()) : stay.cancel());
    return new Written(stay.id(), cancelled.guestId(), cancelled.status().name(), false);
  }

  @GetMapping("/{locator}")
  public Written get(@PathVariable String locator) {
    var stay = stays.findById(stayOf(locator, null)).orElseThrow(() -> new NoSuchElementException("No stay " + locator));
    return new Written(stay.id(), stay.guestId(), stay.status().name(), false);
  }

  /** The room's other people, in pax order: the holder is pax 1 and is not among them. */
  static List<Companion> companions(Reservation r) {
    var list = r.companions() == null ? List.<Person>of() : r.companions();
    var result = new java.util.ArrayList<Companion>();
    for (int i = 0; i < list.size(); i++) {
      var p = list.get(i);
      result.add(new Companion(p.customerId() == null ? "pax-" + (i + 2) : p.customerId(), p.name(), p.document(),
          false, p.email(), p.phone(), "Pax " + (i + 2) + " · de la reserva"));
    }
    return result;
  }

  @ExceptionHandler(NoSuchElementException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  String notFound(NoSuchElementException e) {
    return e.getMessage();
  }

  /** A stay already in the house, or gone, cannot be cancelled from the CRS: the desk decides. */
  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  String conflict(IllegalStateException e) {
    return e.getMessage();
  }

}
