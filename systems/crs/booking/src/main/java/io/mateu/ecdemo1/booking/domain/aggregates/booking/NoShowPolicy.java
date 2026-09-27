package io.mateu.ecdemo1.booking.domain.aggregates.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The CRS's no-show rule: the share of a booking's original price that a guest who does not
 * arrive owes. A policy, not a constant of the booking, because it is the chain's to set.
 */
public record NoShowPolicy(int feePercent) {

    public NoShowPolicy {
        if (feePercent < 0 || feePercent > 100) {
            throw new IllegalArgumentException("A no-show fee is a share of the price, 0 to 100: " + feePercent);
        }
    }

    public BigDecimal feeFor(BigDecimal price) {
        return price.multiply(BigDecimal.valueOf(feePercent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
