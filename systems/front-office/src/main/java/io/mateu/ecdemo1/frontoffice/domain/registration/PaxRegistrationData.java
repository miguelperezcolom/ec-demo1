package io.mateu.ecdemo1.frontoffice.domain.registration;

import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import java.util.Map;

/**
 * The registration data the kárdex keeps of each pax of a stay beyond its identity — nationality,
 * birth date, address… —, as the scanner read it or the desk wrote it.
 */
public interface PaxRegistrationData {

  Map<Field, String> of(String stayId, int pax);

  /** Writes these fields (a blank value clears one); the others stay. */
  void put(String stayId, int pax, Map<Field, String> values);
}
