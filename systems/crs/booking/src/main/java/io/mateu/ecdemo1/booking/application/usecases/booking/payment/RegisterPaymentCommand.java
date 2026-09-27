package io.mateu.ecdemo1.booking.application.usecases.booking.payment;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code date} defaults to today when null. */
public record RegisterPaymentCommand(String id, PaymentType type, String methodCode, BigDecimal amount,
                                     LocalDate date, String reference) {
}
