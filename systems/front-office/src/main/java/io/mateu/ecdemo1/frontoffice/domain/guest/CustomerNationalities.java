package io.mateu.ecdemo1.frontoffice.domain.guest;

import java.util.List;
import java.util.Optional;

/**
 * The nationality of each customer the front office has — a guest or a companion, by their customer
 * code — as the chain's MDM holds it (its golden record, from the {@code customers} topic or asked
 * once), or as the desk took it down for a walk-in. Only for showing: a pax's registration data, when
 * it has one, is what the desk scanned and comes first.
 */
public interface CustomerNationalities {

  /** The customer's nationality, ISO code, if it is known. */
  Optional<String> of(String customerId);

  /** Keeps it; a blank nationality is kept as unknown, so it is not asked for again. */
  void put(String customerId, String nationality, String source);

  /** Guests and companions nobody has said anything about yet, up to {@code limit}. */
  List<String> unknown(int limit);
}
