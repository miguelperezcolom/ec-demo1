package io.mateu.ecdemo1.integration.model.integration;

import java.time.LocalDate;

/** A reservation still to arrive, as a backfill pages through them: in arrival order, by reference. */
public record FutureReservation(String locator, LocalDate arrival, long version) {
}
