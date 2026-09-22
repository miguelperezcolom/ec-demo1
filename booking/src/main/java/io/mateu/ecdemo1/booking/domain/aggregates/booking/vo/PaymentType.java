package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

public enum PaymentType {
    /** Part of the total, collected up front to guarantee the booking. */
    Deposit,
    /** The whole stay, paid before arrival. */
    Prepayment
}
