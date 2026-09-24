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

  public GuestsApi(Kardex kardex) {
    this.kardex = kardex;
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
