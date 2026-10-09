package io.mateu.ecdemo1.frontoffice.domain.cashier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * A stay's credit at the desk: how much may go on the room's account before the desk asks for payment
 * ({@code limit}; null: the check-in's pre-authorization), and whether the credit was <b>cancelled</b> —
 * then the limit is zero and nothing is charged to the room without paying it.
 */
public record CreditTerms(String stayId, BigDecimal limit, boolean cancelled, String reason, String changedBy,
                          Instant changedAt) {

  /** The stored terms of the stays. */
  public interface Repository {
    Optional<CreditTerms> termsOf(String stayId);

    void save(CreditTerms terms);
  }
}
