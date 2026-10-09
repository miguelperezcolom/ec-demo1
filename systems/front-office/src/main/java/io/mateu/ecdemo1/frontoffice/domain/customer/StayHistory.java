package io.mateu.ecdemo1.frontoffice.domain.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A customer's stays in the chain (customer-history), by their MDM code: the summary the desk is shown
 * of a customer it knows — never the detail of what they consumed (GDPR: the desk sees a summary, the
 * detail is for the central office's console). A read the desk can do without: nothing when the
 * service does not answer.
 */
public interface StayHistory {

  /** One of the last stays: where, when, which room. */
  record LastStay(String hotelCode, LocalDate arrival, LocalDate departure, String roomNumber, String roomType) {}

  record HistorySummary(String customerId, int stays, int nights, LocalDate firstStay, LocalDate lastStay,
                        List<LastStay> lastStays, int hotels, String topHotel, BigDecimal spend, String currency) {

    public HistorySummary {
      lastStays = lastStays == null ? List.of() : List.copyOf(lastStays);
    }

    public boolean any() {
      return stays > 0;
    }
  }

  Optional<HistorySummary> summary(String customerCode);

  /** Demo seeding: {@code count} past stays for the customer. Whether the service took it. */
  boolean seedDemo(String customerCode, int count);
}
