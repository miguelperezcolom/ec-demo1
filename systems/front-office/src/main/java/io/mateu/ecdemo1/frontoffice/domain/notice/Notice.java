package io.mateu.ecdemo1.frontoffice.domain.notice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * A reception notice, as the notices service said it last ({@code notices}): of one of the chain's
 * customers (a guest or a companion, by their customer code — Salesforce is its master), of a
 * reservation (by its CRS locator) or of a partner (the agency that sold it, by its code and its name).
 * What the desk must know preparing the arrival, at check-in, while the guests stay, or when they
 * leave; a BLOCKING one must be read, and said so, before the check-in (or the check-out).
 *
 * @param subjectName a partner's name — how the PMS names the agency on a reservation
 * @param hotelCode   the CRS hotel it applies to; null, every hotel of the chain
 * @param version     the notices service's: an older one never replaces a newer one
 */
public record Notice(String noticeId, Subject subject, String subjectId, String subjectName, String hotelCode,
                     long version, String text, Type type, LocalDate from, LocalDate to, Set<Moment> moments,
                     boolean active, Instant updatedAt) {

  public enum Subject { CUSTOMER, RESERVATION, PARTNER }

  public enum Type { INFORMATIVE, IMPORTANT, BLOCKING }

  /** Preparing the arrival, at check-in, while in house, at check-out. */
  public enum Moment { PRE_ARRIVAL, CHECK_IN, IN_HOUSE, CHECK_OUT }

  public Notice {
    moments = moments == null ? Set.of() : Set.copyOf(moments);
    subject = subject == null ? Subject.CUSTOMER : subject;
    type = type == null ? Type.INFORMATIVE : type;
  }

  public boolean blocking() {
    return type == Type.BLOCKING;
  }

  /** Active, of this hotel or of the chain, shown at that moment, and in force some day of the stay. */
  public boolean appliesTo(String hotel, Moment moment, LocalDate arrival, LocalDate departure) {
    return active
        && (hotelCode == null || hotelCode.isBlank() || hotel == null || hotelCode.equalsIgnoreCase(hotel))
        && moments.contains(moment)
        && (from == null || departure == null || !from.isAfter(departure))
        && (to == null || arrival == null || !to.isBefore(arrival));
  }

  /** How the desk names its type. */
  public String typeLabel() {
    return switch (type) {
      case INFORMATIVE -> "Informativo";
      case IMPORTANT -> "Importante";
      case BLOCKING -> "Bloqueante";
    };
  }

  /** How the desk names what it is about. */
  public String subjectLabel() {
    return switch (subject) {
      case CUSTOMER -> "Cliente";
      case RESERVATION -> "Reserva";
      case PARTNER -> "Agencia";
    };
  }

  /** What identifies this version of it: an acknowledgement is of what was read. */
  public String fingerprint() {
    return noticeId + ":" + version;
  }

  /** A stored moment's name: {@code STAY}, the customer notices' old name for the stay, is IN_HOUSE. */
  public static Moment moment(String name) {
    if ("STAY".equals(name)) {
      return Moment.IN_HOUSE;
    }
    try {
      return Moment.valueOf(name);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
