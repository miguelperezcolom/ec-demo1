package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNationalities;
import io.mateu.ecdemo1.frontoffice.domain.registration.PaxRegistrationData;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import org.springframework.stereotype.Service;

/**
 * A pax's nationality, for the flag next to their name: what the desk scanned or wrote in the pax's
 * registration data first — it is the document in hand —, else the customer's as the chain's MDM
 * holds it (or as the desk took it down for a walk-in). Null when nobody has said.
 */
@Service
public class Nationalities {

  final PaxRegistrationData registration;
  final CustomerNationalities customers;

  public Nationalities(PaxRegistrationData registration, CustomerNationalities customers) {
    this.registration = registration;
    this.customers = customers;
  }

  /**
   * @param stayId     the stay, for the pax's registration data; null to skip it
   * @param pax        1 the holder, 2… the companions
   * @param customerId the pax's customer code (the guest's or the companion's id); null if not a customer
   */
  public String of(String stayId, int pax, String customerId) {
    if (stayId != null) {
      var scanned = registration.of(stayId, pax).get(Field.NATIONALITY);
      if (scanned != null && !scanned.isBlank()) {
        return scanned.trim();
      }
    }
    return customers.of(customerId).orElse(null);
  }
}
