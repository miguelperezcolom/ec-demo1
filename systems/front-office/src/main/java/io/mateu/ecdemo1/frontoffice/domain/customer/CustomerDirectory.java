package io.mateu.ecdemo1.frontoffice.domain.customer;

import java.time.LocalDate;
import java.util.List;

/**
 * Who a person is in the chain's customer master (the MDM), asked while the desk waits: the customer a
 * document, an email or a Riu Class member number belongs to — certainty —, and the customers whose
 * name and birth date match — only candidates, never certainty. A read the desk can do without: an
 * MDM that does not answer is {@link Outcome#UNAVAILABLE} or no candidates, never an error.
 *
 * <p>Two writes, for the demo's seeding only: a document of a customer, and their member number.
 */
public interface CustomerDirectory {

  /** What to look a customer up by: exactly one of a document (with its issuing country, if known), an email, a member number. */
  record LookupQuery(String documentNumber, String country, String email, String riuClass) {

    public static LookupQuery byDocument(String number, String country) {
      return new LookupQuery(number, country, null, null);
    }

    public static LookupQuery byEmail(String email) {
      return new LookupQuery(null, null, email, null);
    }

    public static LookupQuery byRiuClass(String memberNumber) {
      return new LookupQuery(null, null, null, memberNumber);
    }
  }

  enum Outcome {
    /** One living customer: certainty. */
    FOUND,
    /** Nobody. */
    NONE,
    /** More than one living customer: no certainty. */
    AMBIGUOUS,
    /** The MDM did not answer (or is not configured): nothing can be said. */
    UNAVAILABLE
  }

  /** A customer as the MDM names them (the survivor, when they were merged). */
  record Customer(String customerId, String status, String firstName, String lastName, LocalDate birthDate) {

    public String name() {
      return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
  }

  /** What a lookup found; {@code customer} only when {@link Outcome#FOUND}, {@code count} when ambiguous. */
  record Lookup(Outcome outcome, Customer customer, String matchedBy, int count) {

    public static Lookup found(Customer customer, String matchedBy) {
      return new Lookup(Outcome.FOUND, customer, matchedBy, 1);
    }

    public static Lookup none() {
      return new Lookup(Outcome.NONE, null, null, 0);
    }

    public static Lookup ambiguous(String matchedBy, int count) {
      return new Lookup(Outcome.AMBIGUOUS, null, matchedBy, count);
    }

    public static Lookup unavailable() {
      return new Lookup(Outcome.UNAVAILABLE, null, null, 0);
    }
  }

  /** A customer whose name and birth date match: someone to ask the guest about, never to merge with. */
  record Candidate(String customerId, String status, String firstName, String lastName, LocalDate birthDate,
                   String nationality, List<String> matched) {

    public String name() {
      return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
  }

  Lookup lookup(LookupQuery query);

  /** Up to five; none without a birth date, or when the MDM does not answer. */
  List<Candidate> candidates(String firstName, String lastName, LocalDate birthDate, String nationality);

  /** Demo seeding: a document of the customer. Whether the MDM took it. */
  boolean addDocument(String customerId, String type, String number, String issuingCountry, LocalDate expiry,
                      String origin);

  /** Demo seeding: the customer's Riu Class member number. Whether the MDM took it. */
  boolean setRiuClass(String customerId, String memberNumber);
}
