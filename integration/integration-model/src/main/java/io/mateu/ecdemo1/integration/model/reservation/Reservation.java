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
                          String cancellationReasonCode,
                          BigDecimal cancellationFee,
                          BigDecimal originalAmount) {

    /** The CRS's cancellation reason for a guest who did not arrive (HLA F006). */
    public static final String NO_SHOW = "NOS";

    public Reservation(String hotelCode, String locator, long version, ReservationStatus status, String channelCode,
                       String partnerCode, String externalReference, LocalDate arrival, LocalDate departure,
                       String currency, Person holder, List<Room> rooms, List<Payment> payments, BigDecimal totalAmount,
                       String comments, String cancellationReasonCode) {
        this(hotelCode, locator, version, status, channelCode, partnerCode, externalReference, arrival, departure,
                currency, holder, rooms, payments, totalAmount, comments, cancellationReasonCode, null, null);
    }

    /** Cancelled because the guest did not arrive: it costs its fee (totalAmount), a share of originalAmount. */
    public boolean noShow() {
        return status == ReservationStatus.CANCELLED && NO_SHOW.equals(cancellationReasonCode);
    }
}
