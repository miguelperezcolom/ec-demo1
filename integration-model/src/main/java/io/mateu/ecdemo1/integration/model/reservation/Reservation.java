package io.mateu.ecdemo1.integration.model.reservation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A reservation in the integration's own terms, as it stands in the CRS right now. Codes are still
 * the CRS's: translating them is the mapping's job, and happens only on the way into a PMS.
 *
 * @param locator the CRS's id for the reservation — with the hotel, its identity everywhere
 * @param version grows with every change in the CRS; what the PMS side orders writes by
 */
public record Reservation(String hotelCode,
                          String locator,
                          long version,
                          ReservationStatus status,
                          String channelCode,
                          String partnerCode,
                          String externalReference,
                          LocalDate arrival,
                          LocalDate departure,
                          String currency,
                          Person holder,
                          List<Room> rooms,
                          List<Payment> payments,
                          BigDecimal totalAmount,
                          String comments,
                          String cancellationReasonCode) {
}
