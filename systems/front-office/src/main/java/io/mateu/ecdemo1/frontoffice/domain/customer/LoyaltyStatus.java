package io.mateu.ecdemo1.frontoffice.domain.customer;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A customer's standing in Riu Class, the chain's loyalty programme (the loyalty service): tier, points,
 * member number, since and as of when. Asked only of a customer the desk KNOWS — the one recognised, not
 * necessarily the stay's guest (a provisional code confirmed as another customer is that customer) —,
 * by their member number when the desk has it, else by their customer code. A read the desk can do
 * without: nothing when the service does not answer, or the customer is not a member.
 */
public interface LoyaltyStatus {

  record Loyalty(String tier, int points, String memberNumber, LocalDate memberSince, LocalDate asOf, String source) {}

  /** {@code riuClassNumber} may be null: then by the customer's code. */
  Optional<Loyalty> of(String customerCode, String riuClassNumber);

  /** Demo seeding: the customer as a member, with this tier and points. Whether the service took it. */
  boolean enroll(String memberNumber, String customerCode, String tier, int points, LocalDate memberSince);
}
