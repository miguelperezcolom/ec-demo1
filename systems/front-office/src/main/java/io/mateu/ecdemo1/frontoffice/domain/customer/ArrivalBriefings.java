package io.mateu.ecdemo1.frontoffice.domain.customer;

import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory.HistorySummary;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * What the desk knows of an arriving pax before they arrive: the customer the reservation names, with
 * the summary of their stays and their Riu Class standing — prepared ahead, so the arrivals list shows
 * the returning customers and the check-in does not wait for customer-history or loyalty.
 */
public interface ArrivalBriefings {

  /** One pax's briefing; {@code loyalty} null when they are not a member, or loyalty did not answer. */
  record Briefing(String stayId, int pax, String customerId, String customerName, HistorySummary history,
                  LoyaltyStatus.Loyalty loyalty, Instant preparedAt) {

    public Optional<LoyaltyStatus.Loyalty> riuClass() {
      return Optional.ofNullable(loyalty);
    }
  }

  Optional<Briefing> of(String stayId, int pax);

  /** Every briefing, of every stay. */
  List<Briefing> all();

  void save(Briefing briefing);

  void clear(String stayId, int pax);

  /** Removes the briefings of every stay not in {@code stayIds}: those no longer arriving. Returns how many. */
  int keepOnly(Collection<String> stayIds);
}
