package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

import java.time.Instant;

/**
 * Why and when a booking was cancelled — and, for a no-show, what it still costs: a share of its
 * original price the guest owes for not arriving.
 *
 * @param fee        what the cancellation costs; null when it costs nothing
 * @param feePercent the share of the original price the fee is
 */
public record Cancellation(String reasonCode, Instant cancelledAt, java.math.BigDecimal fee, Integer feePercent) {

    public Cancellation(String reasonCode, Instant cancelledAt) {
        this(reasonCode, cancelledAt, null, null);
    }
}
