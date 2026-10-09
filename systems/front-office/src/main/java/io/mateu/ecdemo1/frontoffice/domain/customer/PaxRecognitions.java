package io.mateu.ecdemo1.frontoffice.domain.customer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Who each pax of a stay was recognized as at the desk, and the last document scanned of them — kept,
 * not recomputed: the screens are rebuilt on every interaction, and the MDM is asked once per scan.
 */
public interface PaxRecognitions {

  enum Certainty {
    /** The pax is this customer: their history may be shown. */
    KNOWN,
    /** The pax may be one of the candidates: the desk asks the guest; no history is shown. */
    POSSIBLE
  }

  enum MatchedBy { DOCUMENT, EMAIL, RIU_CLASS, CANDIDATE, CHAIN_CODE }

  /** Someone the pax may be: what the desk can ask about — name and birth date, nothing else. */
  record Named(String customerId, String name, LocalDate birthDate) {}

  /**
   * What the pax was recognised as. {@code customerId}/{@code customerName}: the customer, when KNOWN
   * (or the one candidate a document pointed at); {@code riuClass}: the member number the desk confirmed
   * them with, when it did so by it.
   */
  record PaxRecognition(String stayId, int pax, String customerId, String customerName, Certainty certainty,
                        MatchedBy matchedBy, List<Named> candidates, Instant confirmedAt, String confirmedBy,
                        String riuClass) {

    public PaxRecognition {
      candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public boolean confirmed() {
      return confirmedAt != null;
    }
  }

  /**
   * The last document scanned of a pax, and what the MDM said of it ({@code lookup}: FOUND, NONE,
   * AMBIGUOUS, UNAVAILABLE; {@code foundCustomerId} when found) — what a later confirmation sends the
   * MDM again, with the customer confirmed.
   */
  record PaxScan(String stayId, int pax, String firstName, String lastName, String documentType, String documentNumber,
                 LocalDate birthDate, String nationality, String issuingCountry, LocalDate expiry, String lookup,
                 String foundCustomerId) {}

  Optional<PaxRecognition> of(String stayId, int pax);

  void save(PaxRecognition recognition);

  void clear(String stayId, int pax);

  Optional<PaxScan> scanOf(String stayId, int pax);

  void saveScan(PaxScan scan);
}
