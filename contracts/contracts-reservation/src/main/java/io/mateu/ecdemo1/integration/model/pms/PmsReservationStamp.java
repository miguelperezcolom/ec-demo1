package io.mateu.ecdemo1.integration.model.pms;

import java.time.LocalDate;

/**
 * One reservation of a PMS property, as far as knowing whether it changed: its id, its dates, and
 * when the PMS last modified it (ISO local date-time). What the pms-fo integration's backfill and
 * polling page through.
 */
public record PmsReservationStamp(String pmsReservationId, String confirmationNumber, LocalDate arrival,
                                  LocalDate departure, String status, String lastModified) {
}
