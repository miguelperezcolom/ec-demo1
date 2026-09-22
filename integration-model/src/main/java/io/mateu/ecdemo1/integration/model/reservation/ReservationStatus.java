package io.mateu.ecdemo1.integration.model.reservation;

public enum ReservationStatus {
    /** Sold, payment not verified yet. Still a sale: it is projected like a confirmed one. */
    PENDING,
    CONFIRMED,
    CANCELLED
}
