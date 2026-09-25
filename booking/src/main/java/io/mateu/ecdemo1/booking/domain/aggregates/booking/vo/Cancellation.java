package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

import java.time.Instant;

public record Cancellation(String reasonCode, Instant cancelledAt) {
}
