package io.mateu.ecdemo1.frontoffice.infra.api;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
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
 * what the reservation says, never what happened at the hotel.
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
  public record Reservation(Person holder, List<Person> companions, String roomType, String board,
      LocalDate checkIn, LocalDate checkOut, int pax, String agency, BigDecimal total) {}

  public record Written(String stayId, String guestId, String status, boolean created) {}

  final StayRepository stays;
  final GuestRepository guests;

  public ReservationsApi(StayRepository stays, GuestRepository guests) {
    this.stays = stays;
    this.guests = guests;
  }

  @PutMapping("/{locator}")
  @Transactional
  public Written write(@PathVariable String locator, @RequestBody Reservation r) {
    var holder = r.holder();
    var guestId = holder.customerId() == null || holder.customerId().isBlank() ? "crs-" + locator : holder.customerId();
    var guest = guests.findById(guestId)
        .map(g -> g.withReservationData(holder.name(), holder.document(), holder.email(), holder.phone()))
        .orElseGet(() -> Guest.fromReservation(guestId, holder.name(), holder.document(), holder.email(), holder.phone()));
    guests.save(guest);
    var companions = companions(r);
    var existing = stays.findById(locator);
    var stay = existing
        .map(s -> s.applyReservation(guestId, r.roomType(), r.board(), r.checkIn(), r.checkOut(), r.pax(), r.agency(),
            r.total(), companions))
        .orElseGet(() -> Stay.fromReservation(locator, guestId, r.roomType(), r.board(), r.checkIn(), r.checkOut(),
            r.pax(), r.agency(), r.total(), companions));
    stays.save(stay);
    return new Written(locator, guestId, stay.status().name(), existing.isEmpty());
  }

  @PostMapping("/{locator}/cancellation")
  @Transactional
  public Written cancel(@PathVariable String locator) {
    var stay = stays.findById(locator).orElseThrow(() -> new NoSuchElementException("No stay " + locator));
    var cancelled = stays.save(stay.cancel());
    return new Written(locator, cancelled.guestId(), cancelled.status().name(), false);
  }

  @GetMapping("/{locator}")
  public Written get(@PathVariable String locator) {
    var stay = stays.findById(locator).orElseThrow(() -> new NoSuchElementException("No stay " + locator));
    return new Written(locator, stay.guestId(), stay.status().name(), false);
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
