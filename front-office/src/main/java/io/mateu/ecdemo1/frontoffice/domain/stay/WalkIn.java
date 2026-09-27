package io.mateu.ecdemo1.frontoffice.domain.stay;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A stay the desk opened for a guest with no reservation. The CRS owns every booking, so the stay is
 * the hotel's at once — the guest checks in now — and the booking follows: sent to the CRS under the
 * stay's own id as reference, at the price the CRS quoted; it comes back down the chain as any other
 * reservation, and is recognised by that reference.
 *
 * @param stayId   the front office's reference, FO-…, the stay's id for good
 * @param request  what is sent to the CRS, as sent: sent again, unchanged, until the CRS answers
 * @param locator  the CRS's booking, once it made it
 * @param pmsReservationId Opera's, once the booking came back down with it
 * @param message  why the CRS refused it, if it did
 */
public record WalkIn(String stayId, String request, BigDecimal expectedTotal, WalkInStatus status, String locator,
                     String pmsReservationId, String message, Instant createdAt, Instant bookedAt) {

  public enum WalkInStatus {
    /** Made here; the CRS has not answered yet. */
    PENDING,
    /** The CRS made the booking. */
    BOOKED,
    /** The CRS refused it — a price that changed, a code it no longer has: the desk has to see to it. */
    REFUSED
  }

  public static WalkIn pending(String stayId, String request, BigDecimal expectedTotal, Instant now) {
    return new WalkIn(stayId, request, expectedTotal, WalkInStatus.PENDING, null, null, null, now, null);
  }

  public WalkIn booked(String locator, Instant now) {
    return new WalkIn(stayId, request, expectedTotal, WalkInStatus.BOOKED, locator, pmsReservationId, null, createdAt,
        bookedAt == null ? now : bookedAt);
  }

  public WalkIn refused(String why) {
    return new WalkIn(stayId, request, expectedTotal, WalkInStatus.REFUSED, locator, pmsReservationId, why, createdAt,
        bookedAt);
  }

  /** The booking came back down the chain: the CRS's locator, and Opera's reservation when it has one. */
  public WalkIn cameBack(String locator, String pmsReservationId, Instant now) {
    return new WalkIn(stayId, request, expectedTotal, WalkInStatus.BOOKED, locator,
        pmsReservationId == null || pmsReservationId.isBlank() ? this.pmsReservationId : pmsReservationId, null,
        createdAt, bookedAt == null ? now : bookedAt);
  }

  /** How the desk reads it. */
  public String label() {
    return switch (status) {
      case PENDING -> "Walk-in · pendiente del CRS";
      case BOOKED -> "Walk-in · CRS " + locator + (pmsReservationId == null ? "" : " · Opera " + pmsReservationId);
      case REFUSED -> "Walk-in · el CRS no la acepta: " + message;
    };
  }
}
