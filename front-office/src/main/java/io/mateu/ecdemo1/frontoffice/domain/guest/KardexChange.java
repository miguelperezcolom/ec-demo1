package io.mateu.ecdemo1.frontoffice.domain.guest;

import java.time.Instant;

/**
 * The last change the desk made to a guest's data that the chain's master — Salesforce, through the
 * MDM — has to decide. The cardex shows the new data at once, marked as pending; approved, it stays;
 * rejected, the master's data comes back.
 *
 * @param requestId the MDM's change request, once it was sent there
 * @param synced    whether the MDM has it: an edit made while the MDM does not answer is sent later
 */
public record KardexChange(String guestId, String requestId, KardexStatus status, String changes,
                           Instant requestedAt, Instant decidedAt, boolean synced) {

  public enum KardexStatus { PENDING, APPROVED, REJECTED }

  public static KardexChange pending(String guestId, String changes, Instant now) {
    return new KardexChange(guestId, null, KardexStatus.PENDING, changes, now, null, false);
  }

  public KardexChange sent(String requestId) {
    return new KardexChange(guestId, requestId, status, changes, requestedAt, decidedAt, true);
  }

  public KardexChange decided(KardexStatus decision, Instant now) {
    return new KardexChange(guestId, requestId, decision, changes, requestedAt, now, true);
  }

  public boolean pending() {
    return status == KardexStatus.PENDING;
  }

  /** How the cardex reads it. */
  public String label() {
    return switch (status) {
      case PENDING -> "Pendiente de aprobación";
      case APPROVED -> "Aprobado";
      case REJECTED -> "Rechazado";
    };
  }
}
