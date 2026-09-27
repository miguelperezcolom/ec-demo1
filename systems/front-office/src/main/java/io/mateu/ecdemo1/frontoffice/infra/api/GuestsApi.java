package io.mateu.ecdemo1.frontoffice.infra.api;

import io.mateu.ecdemo1.frontoffice.infra.mdm.Kardex;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Where the chain's MDM tells the kardex how a guest is: Salesforce is the master of the customer's data. */
@RestController
@RequestMapping("/api/guests")
public class GuestsApi {

  final Kardex kardex;
  final io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository guests;
  final io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository stays;

  public GuestsApi(Kardex kardex, io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository guests,
      io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository stays) {
    this.kardex = kardex;
    this.guests = guests;
    this.stays = stays;
  }

  /**
   * A stay a person is in, for the chain's customer master: its id is the CRS locator.
   *
   * @param role HOLDER — the guest the stay belongs to — or COMPANION
   */
  public record StayView(String id, String role, java.time.LocalDate checkIn, java.time.LocalDate checkOut,
      String room, String roomType, String status) {}

  /** The stays of a person by the chain's customer code, latest first; empty if the hotel never had them. */
  @org.springframework.web.bind.annotation.GetMapping("/{id}/stays")
  public java.util.List<StayView> stays(@PathVariable String id) {
    return stays.findByPerson(id).stream()
        .map(s -> new StayView(s.id(), id.equals(s.guestId()) ? "HOLDER" : "COMPANION", s.checkIn(), s.checkOut(),
            s.roomNumber(), s.roomType(), s.status().name()))
        .toList();
  }

  /** A guest of the kardex, and how its last change to the master's data stands. */
  public record GuestView(String id, String name, String document, String email, String phone, String kardexStatus,
      String kardexChanges, String kardexReason) {}

  @org.springframework.web.bind.annotation.GetMapping("/{id}")
  public ResponseEntity<GuestView> guest(@PathVariable String id) {
    return guests.findById(id).map(g -> {
      var change = kardex.of(id).orElse(null);
      return ResponseEntity.ok(new GuestView(g.id(), g.name(), g.document(), g.email(), g.phone(),
          change == null ? null : change.status().name(), change == null ? null : change.changes(),
          change == null ? null : change.reason()));
    }).orElse(ResponseEntity.notFound().build());
  }

  /**
   * A change made in the master, or its decision on the desk's change. 404 if this front office does
   * not have the guest.
   */
  @PutMapping("/{id}/kardex")
  public ResponseEntity<Void> kardex(@PathVariable String id, @RequestBody Kardex.Update update) {
    return kardex.projected(id, update) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}
