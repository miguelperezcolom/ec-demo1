package io.mateu.ecdemo1.frontoffice.domain.stay;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;

/**
 * What an arriving stay still needs before the guest can check in: the identity of each pax (the
 * holder from the guest's cardex, the others from the stay's companions), a room, and the ancillaries
 * selection closed. A pax the desk marked as a no-show is no longer waited for.
 */
public final class CheckInChecklist {

  private CheckInChecklist() {}

  /** How many of the stay's pax still lack a verified identity (the holder included). */
  public static int pendingPax(Stay stay, Guest holder, CheckInOps ops) {
    int pending = 0;
    if (!ops.isNoShow(1) && (holder == null || !holder.identityComplete())) {
      pending++;
    }
    var companions = stay.companions();
    for (int i = 0; i < companions.size(); i++) {
      if (!ops.isNoShow(i + 2) && !companions.get(i).identityComplete()) {
        pending++;
      }
    }
    for (int paxN = 2 + companions.size(); paxN <= stay.pax(); paxN++) {
      if (!ops.isNoShow(paxN)) {
        pending++;
      }
    }
    return pending;
  }

  /**
   * Nothing the check-in wizard would ask for is missing — every pax registered, a room assigned and
   * the ancillaries selection closed — so the desk can check the stay in without questions.
   */
  public static boolean readyForDirectCheckIn(Stay stay, Guest holder, CheckInOps ops) {
    return pendingPax(stay, holder, ops) == 0 && stay.hasRoom() && ops.extras();
  }

  /** Whether every pax of the reservation is marked as a no-show: nobody of it arrived. */
  public static boolean nobodyArrived(Stay stay, CheckInOps ops) {
    for (int pax = 1; pax <= stay.pax(); pax++) {
      if (!ops.isNoShow(pax)) {
        return false;
      }
    }
    return true;
  }
}
