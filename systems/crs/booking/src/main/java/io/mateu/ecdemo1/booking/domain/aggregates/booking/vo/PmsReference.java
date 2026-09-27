package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

import java.time.Instant;

/**
 * Where the booking lives in the property's PMS, written back by the integration so an operator
 * of the CRS can find it there. It is not a commercial change and does not version the booking.
 */
public record PmsReference(String reservationId, Instant annotatedAt) {
}
