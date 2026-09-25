package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money the central office has already collected for a booking.
 *
 * <p>{@code paymentId} is stable for the life of the payment. It is what lets the PMS side apply a
 * payment once however many times the booking is projected.
 */
public record Payment(String paymentId,
                      PaymentType type,
                      String methodCode,
                      BigDecimal amount,
                      LocalDate date,
                      String reference) {

    public Payment {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("A payment needs an id");
        }
        if (type == null || methodCode == null || methodCode.isBlank() || date == null) {
            throw new IllegalArgumentException("A payment needs a type, a method and a date");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("A payment needs a positive amount");
        }
    }
}
