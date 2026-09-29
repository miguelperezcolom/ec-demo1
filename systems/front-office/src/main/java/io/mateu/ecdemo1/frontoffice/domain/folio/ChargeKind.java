package io.mateu.ecdemo1.frontoffice.domain.folio;

/**
 * What a line of the folio is. The PMS — the master of the folio — charges the accommodation itself;
 * every other charge of the desk goes onto the PMS's folio too (pms-fo, «registrar-cargo»), so that the
 * PMS's invoice covers it.
 */
public enum ChargeKind {
  /** The stay's nights: the PMS posts them itself (its room charge). */
  ACCOMMODATION,
  /** An extra contracted at the check-in: its code is the add-on's. */
  ADD_ON,
  /** The late check-out. */
  LATE_CHECK_OUT,
  /** A consumption from the desk's charge catalogue (minibar, room service…): its code is the catalogue's. */
  CONSUMPTION;

  /** Whether a charge of this kind goes onto the PMS's folio. */
  public boolean toThePms() {
    return this != ACCOMMODATION;
  }
}
