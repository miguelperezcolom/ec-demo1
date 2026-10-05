package io.mateu.ecdemo1.frontoffice.domain.stay;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import java.util.ArrayList;
import java.util.List;

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
   * The pax the desk goes on to after {@code after} (1 = the holder): the next one, in order and
   * wrapping round, that still lacks a verified identity and is not a no-show; 0 when none is left.
   */
  public static int nextPendingPax(Stay stay, Guest holder, CheckInOps ops, int after) {
    int pax = Math.max(stay.pax(), 1);
    int from = after < 1 || after > pax ? pax : after;
    for (int k = 1; k <= pax; k++) {
      int candidate = (from - 1 + k) % pax + 1;
      if (paxPending(stay, holder, ops, candidate)) {
        return candidate;
      }
    }
    return 0;
  }

  /** Whether pax {@code paxN} (1 = the holder) still lacks a verified identity, no-shows aside. */
  static boolean paxPending(Stay stay, Guest holder, CheckInOps ops, int paxN) {
    if (ops.isNoShow(paxN)) {
      return false;
    }
    if (paxN == 1) {
      return holder == null || !holder.identityComplete();
    }
    var companions = stay.companions();
    return paxN - 2 >= companions.size() || !companions.get(paxN - 2).identityComplete();
  }

  /**
   * What the check-in still lacks to be complete: the identity document of each pax (no-shows aside)
   * and the guest's signature on the registration card. Empty, the stay can check in; otherwise only a
   * forced check-in lets it in, and it stays incomplete until these are done.
   */
  public static List<PendingStep> missing(Stay stay, Guest holder, CheckInOps ops) {
    var missing = new ArrayList<PendingStep>();
    if (!ops.isNoShow(1) && (holder == null || !holder.identityComplete())) {
      missing.add(PendingStep.document(1, holder == null ? "el titular" : holder.name()));
    }
    for (int paxN = 2; paxN <= stay.pax(); paxN++) {
      var companion = stay.companionAt(paxN);
      if (!ops.isNoShow(paxN) && (companion == null || !companion.identityComplete())) {
        missing.add(PendingStep.document(paxN, companion == null ? "Huésped " + paxN : companion.name()));
      }
    }
    if (!ops.firma()) {
      missing.add(PendingStep.signature());
    }
    return List.copyOf(missing);
  }

  /**
   * Nothing the check-in wizard would ask for is missing — every pax registered, the registration
   * signed, a room assigned and the ancillaries selection closed — so the desk can check the stay in without questions.
   */
  public static boolean readyForDirectCheckIn(Stay stay, Guest holder, CheckInOps ops) {
    return missing(stay, holder, ops).isEmpty() && stay.hasRoom() && ops.extras();
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
