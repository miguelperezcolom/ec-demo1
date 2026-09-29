package io.mateu.ecdemo1.frontoffice.domain.guest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * A reception notice of one of the chain's customers — a guest or a companion, by their customer code
 * — as the MDM said it last (customer-notices); Salesforce is its master. What the desk must know when
 * they arrive, stay or leave; a BLOCKING one must be read, and said so, before the check-in.
 *
 * @param version the MDM's: an older one never replaces a newer one
 */
public record CustomerNotice(String noticeId, String customerId, long version, String text, Type type,
                             LocalDate from, LocalDate to, Set<Moment> showAt, boolean active, Instant updatedAt) {

  public enum Type { INFORMATIVE, IMPORTANT, BLOCKING }

  public enum Moment { CHECK_IN, CHECK_OUT, STAY }

  public CustomerNotice {
    showAt = showAt == null ? Set.of() : Set.copyOf(showAt);
  }

  public boolean blocking() {
    return type == Type.BLOCKING;
  }

  /** Active, shown at that moment, and in force some day of the stay. */
  public boolean appliesTo(Moment moment, LocalDate arrival, LocalDate departure) {
    return active && showAt.contains(moment)
        && (from == null || departure == null || !from.isAfter(departure))
        && (to == null || arrival == null || !to.isBefore(arrival));
  }

  /** How the desk names its type. */
  public String typeLabel() {
    return switch (type == null ? Type.INFORMATIVE : type) {
      case INFORMATIVE -> "Informativo";
      case IMPORTANT -> "Importante";
      case BLOCKING -> "Bloqueante";
    };
  }

  /** What identifies this version of it: an acknowledgement is of what was read. */
  public String fingerprint() {
    return noticeId + ":" + version;
  }
}
