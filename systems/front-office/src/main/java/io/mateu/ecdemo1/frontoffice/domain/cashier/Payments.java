package io.mateu.ecdemo1.frontoffice.domain.cashier;

import java.util.List;
import java.util.Optional;

/** The payments taken on stays' accounts. */
public interface Payments {

  Payment save(Payment payment);

  Optional<Payment> byId(String id);

  Optional<Payment> byLinkToken(String token);

  /** A stay's payments, oldest first. */
  List<Payment> of(String stayId);

  /** The next receipt number of the hotel's desk: one sequence for every receipt. */
  int nextReceiptNo();
}
