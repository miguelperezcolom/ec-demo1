package io.mateu.ecdemo1.frontoffice.domain.guest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The part of a pax's kárdex the registration data does not hold — the name split in first name and
 * surnames, the Riu Class number, the document's issue date, language, province, fax and advertising
 * consent —, as the desk filled it in with the guest. A pax without one has a <b>provisional</b> kárdex:
 * only what the reservation and a scan said.
 */
public interface PaxKardexes {

  record PaxKardex(String stayId, int pax, String firstName, String lastName, String riuClass,
                   LocalDate documentIssueDate, String language, String province, String fax, Boolean marketingConsent,
                   Instant filledAt, String filledBy) {}

  Optional<PaxKardex> of(String stayId, int pax);

  void save(PaxKardex kardex);
}
