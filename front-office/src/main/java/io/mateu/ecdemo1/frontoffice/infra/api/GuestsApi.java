package io.mateu.ecdemo1.frontoffice.infra.api;

import io.mateu.ecdemo1.frontoffice.infra.mdm.Kardex;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Where the chain's MDM tells the cardex how a guest is: Salesforce is the master of the customer's data. */
@RestController
@RequestMapping("/api/guests")
public class GuestsApi {

  final Kardex kardex;
  final io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository guests;

  public GuestsApi(Kardex kardex, io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository guests) {
    this.kardex = kardex;
    this.guests = guests;
  }

  /** A guest of the cardex, and how its last change to the master's data stands. */
  public record GuestView(String id, String name, String document, String email, String phone, String kardexStatus,
      String kardexChanges) {}

  @org.springframework.web.bind.annotation.GetMapping("/{id}")
  public ResponseEntity<GuestView> guest(@PathVariable String id) {
    return guests.findById(id).map(g -> {
      var change = Kardex.of(id).orElse(null);
      return ResponseEntity.ok(new GuestView(g.id(), g.name(), g.document(), g.email(), g.phone(),
          change == null ? null : change.status().name(), change == null ? null : change.changes()));
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
