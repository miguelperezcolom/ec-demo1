package io.mateu.ecdemo1.integration.model.reservation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money already collected by the central office. {@code paymentId} is stable, so a PMS applies it
 * once however many times the reservation is projected.
 */
public record Payment(String paymentId,
                      PaymentType type,
                      String methodCode,
                      BigDecimal amount,
                      LocalDate date,
                      String reference) {
}
