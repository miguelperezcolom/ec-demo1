package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The price of one night of one room, in the booking's currency. */
public record NightlyRate(LocalDate date, BigDecimal amount) {

    public NightlyRate {
        if (date == null || amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("A nightly rate needs a date and a non-negative amount");
        }
    }
}
